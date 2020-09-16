package com.rasterfoundry.granary.database

import com.rasterfoundry.granary.datamodel._

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.applicative._
import cats.syntax.list._
import com.amazonaws.services.batch.model.ClientException
import doobie._
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import doobie.refined.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.DecodingFailure
import io.circe.schema.ValidationError
import io.circe.syntax._

import java.util.UUID

object ExecutionDao {

  sealed abstract class ExecutionDaoError extends Throwable
  case object TaskNotFound                extends ExecutionDaoError

  case class ArgumentsValidationFailed(underlying: NonEmptyList[ValidationError])
      extends ExecutionDaoError
  case class BatchSubmissionFailed(msg: String) extends ExecutionDaoError

  @inline def batchSafeJobName(s: String): String =
    s.replace(" ", "-")

  val selectF =
    fr"""
      SELECT
        id, task_id, invoked_at, arguments, status,
        status_reason, results, webhook_id, owner, name, tags
      FROM executions
    """

  def listExecutions(
      token: Token,
      pageRequest: PageRequest,
      taskId: Option[UUID],
      status: Option[JobStatus],
      name: Option[String],
      tags: List[NonEmptyString]
  ): ConnectionIO[List[Execution]] =
    Page(
      selectF ++ Fragments.whereAndOpt(
        taskId map { id => fr"task_id = $id" },
        status map { s =>
          fr"status = $s"
        },
        tokenToFilter(token),
        name map { s =>
          Fragment.const(s"name like '%$s%'")
        },
        tags.toNel map { _ =>
            fr"""tags @> $tags :: text[]"""
        }
      ),
      pageRequest
    ).query[Execution]
      .to[List]

  def getQuery(token: Option[Token], id: UUID) =
    (selectF ++ Fragments
      .whereAndOpt(
        Some(fr"id = $id"),
        token flatMap { tokenToFilter }
      )).query[Execution]

  def getExecution(token: Option[Token], id: UUID): ConnectionIO[Option[Execution]] =
    getQuery(token, id).option

  def unsafeGetExecution(token: Option[Token], id: UUID): ConnectionIO[Execution] =
    getQuery(token, id).unique

  def kickOffExecutionJob(
      execution: Execution,
      task: Task,
      dataBucket: String,
      apiHost: String
  ): EitherT[ConnectionIO, ExecutionDaoError, Execution] = {

    def updateFailure(
        execution: Execution
    ): Throwable => EitherT[ConnectionIO, ExecutionDaoError, Execution] =
      (e: Throwable) => {
        val resultErr = e match {
          case err: DecodingFailure =>
            ExecutionDao.BatchSubmissionFailed(
              s"Could not decode arguments to Map[String, String] for execution ${execution.id}: ${err.getMessage}"
            )
          case err: ClientException =>
            ExecutionDao.BatchSubmissionFailed(
              s"Client exception with AWS batch for execution ${execution.id}: ${err.getMessage}"
            )
        }
        val newExecution =
          execution.copy(status = JobStatus.Failed, statusReason = Some(e.getMessage))
        EitherT {
          (fr"""
          update executions
          set status = ${newExecution.status}, status_reason=${newExecution.statusReason}
        """ ++ Fragments.whereOr(fr"id = ${execution.id}")).update.run map { _ => Left(resultErr) }
        }
      }

    def updateSuccess(
        execution: Execution
    )(u: Unit): EitherT[ConnectionIO, ExecutionDaoError, Execution] = {
      // trick to suppress "pure expression in statement position" warning
      locally(u)
      val newStatus: JobStatus = JobStatus.Started
      EitherT {
        (fr"update executions set status = $newStatus" ++ Fragments.whereOr(
          fr"id = ${execution.id}"
        )).update
          .withUniqueGeneratedKeys[Execution](
            "id",
            "task_id",
            "invoked_at",
            "arguments",
            "status",
            "status_reason",
            "results",
            "webhook_id",
            "owner",
            "name",
            "tags"
          ) map { Right(_) }
      }
    }

    EitherT(
      AWSBatch
        .submitJobRequest[ConnectionIO](
          task.jobDefinition,
          task.jobQueue,
          execution.arguments
            .deepMerge(
              execution.webhookId map { webhookId =>
                Map(
                  "WEBHOOK_URL" -> s"${apiHost}/executions/${execution.id}/results/${webhookId}"
                ).asJson
              } getOrElse { ().asJson }
            ),
          batchSafeJobName(s"${task.name}-${execution.id}"),
          dataBucket
        )
    ).biflatMap(updateFailure(execution), updateSuccess(execution))
  }

  def insertExecution(
      token: Token,
      execution: Execution.Create,
      dataBucket: String,
      apiHost: String
  ): ConnectionIO[Either[ExecutionDaoError, Execution]] = {
    val owner    = tokenToUserId(token)
    val fragment = fr"""
      INSERT INTO executions
        (id, task_id, invoked_at, arguments, status, status_reason, results, webhook_id, owner, name, tags)
      VALUES
        (uuid_generate_v4(), ${execution.taskId}, now(), ${execution.arguments},
        'CREATED', NULL, '[]' :: jsonb, uuid_generate_v4(), $owner, ${execution.name}, ${execution.tags})
    """
    val insertIO: OptionT[ConnectionIO, Either[ExecutionDaoError, Execution]] = for {
      task <- OptionT { TaskDao.getTask(token, execution.taskId) }
      argCheck = task.validator.validate(execution.arguments)
      insert <- OptionT.liftF {
        argCheck match {
          case Invalid(errs) =>
            Left(ArgumentsValidationFailed(errs): ExecutionDaoError).pure[ConnectionIO]
          case Valid(()) =>
            fragment.update.withUniqueGeneratedKeys[Execution](
              "id",
              "task_id",
              "invoked_at",
              "arguments",
              "status",
              "status_reason",
              "results",
              "webhook_id",
              "owner",
              "name",
              "tags"
            ) map { Right(_) }
        }
      }
      updated <- OptionT.liftF {
        (EitherT.fromEither[ConnectionIO](insert) flatMap {
          kickOffExecutionJob(_, task, dataBucket, apiHost)
        }).value
      }
    } yield updated

    insertIO.value map {
      case Some(res) => res
      case None      => Left(TaskNotFound)
    }
  }

  def addResults(
      executionId: UUID,
      webhookId: UUID,
      status: ExecutionStatusUpdate
  ): OptionT[ConnectionIO, Execution] =
    for {
      existingExecution <- OptionT(getExecution(None, executionId)) flatMap {
        case pred if pred.webhookId == Some(webhookId) => OptionT.some(pred)
        case _                                         => OptionT.none[ConnectionIO, Execution]
      }
      newExecution = status match {
        case ExecutionSuccess(results) =>
          existingExecution.copy(
            status = JobStatus.Successful,
            results = results,
            webhookId = None
          )
        case ExecutionFailure(reason) =>
          existingExecution.copy(
            status = JobStatus.Failed,
            statusReason = Some(reason),
            webhookId = None
          )
      }
      update <- OptionT.liftF {
        fr"""
        UPDATE executions
        SET
          status = ${newExecution.status},
          status_reason = ${newExecution.statusReason},
          results = ${newExecution.results},
          webhook_id = ${newExecution.webhookId}
        WHERE
          id = ${executionId}
      """.update.withUniqueGeneratedKeys[Execution](
          "id",
          "task_id",
          "invoked_at",
          "arguments",
          "status",
          "status_reason",
          "results",
          "webhook_id",
          "owner",
          "name",
          "tags"
        )
      }
    } yield update
}
