package com.rasterfoundry.granary.api.services

import java.util.UUID

import cats._
import cats.effect._
import cats.syntax.all._
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.api.auth._
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.database.ExecutionDao
import com.rasterfoundry.granary.datamodel._
import doobie._
import doobie.implicits._
import io.circe.syntax._
import org.http4s._
import shapeless.syntax.std.tuple._
import sttp.tapir.server.http4s._

class ExecutionService[F[_]: Sync](
    defaultPageRequest: PageRequest,
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F],
    dataBucket: String,
    apiHost: String,
    auth: Auth[F]
)(implicit
    contextShift: ContextShift[F]
) extends GranaryService {
  private val s3Client = AmazonS3ClientBuilder.defaultClient()

  def listExecutions(
      token: Token,
      pageRequest: PageRequest,
      taskId: Option[UUID],
      status: Option[JobStatus],
      name: Option[String]
  ): F[Either[CrudError, PaginatedResponse[Execution]]] = {
    val forPage = pageRequest `combine` defaultPageRequest
    mkContext("listExecutions", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        ExecutionDao
          .listExecutions(token, forPage, taskId, status, name)
          .transact(xa)
      ) { executions =>
        val updatedExecutions = executions.map(_.signS3OutputLocation(s3Client))
        Right(PaginatedResponse.forRequest(updatedExecutions, forPage))
      }
    }
  }

  def getById(token: Token, id: UUID): F[Either[CrudError, Execution]] =
    mkContext("lookupExecutionById", Map("executionId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(
        ExecutionDao.getExecution(Some(token), id).transact(xa)
      ) {
        case Some(execution) =>
          Right(execution.signS3OutputLocation(s3Client))
        case None => Left(NotFound())
      }
    }

  def createExecution(
      token: Token,
      execution: Execution.Create
  ): F[Either[CrudError, Execution]] =
    mkContext("createExecution", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        ExecutionDao
          .insertExecution(token, execution, dataBucket, apiHost)
          .transact(xa)
      )({
        case Right(created)                  => Right(created)
        case Left(ExecutionDao.TaskNotFound) => Left(NotFound())
        case Left(ExecutionDao.ArgumentsValidationFailed(errs)) =>
          Left(ValidationError(errs map { _.getMessage } reduce))
        case Left(ExecutionDao.BatchSubmissionFailed(msg)) =>
          Left(
            ValidationError(
              s"Batch resources for task ${execution.taskId} may be misconfigured: $msg"
            )
          )
      })
    }

  def addExecutionResults(
      executionId: UUID,
      executionWebhookId: UUID,
      updateMessage: ExecutionStatusUpdate
  ): F[Either[CrudError, Execution]] =
    mkContext(
      "addExecutionResults",
      Map("statusUpdate" -> updateMessage.asJson.noSpaces),
      contextBuilder
    ) use { _ =>
      Functor[F].map(
        ExecutionDao
          .addResults(executionId, executionWebhookId, updateMessage)
          .value
          .transact(xa)
      )({
        case None =>
          Left(NotFound())
        case Some(p) =>
          Right(p.signS3OutputLocation(s3Client))
      })
    }

  val list = ExecutionEndpoints.list
    .serverLogicPart(auth.fallbackToForbidden)
    .andThen({
      case (token, rest) =>
        val tupled = Function.tupled(listExecutions _)
        tupled(token +: rest)
    })
    .toRoutes

  val detail = ExecutionEndpoints.idLookup
    .serverLogicPart(auth.fallbackToForbidden)
    .andThen({
      case (token, rest) => getById(token, rest)
    })
    .toRoutes

  val create = ExecutionEndpoints.create
    .serverLogicPart(auth.fallbackToForbidden)
    .andThen({
      case (token, rest) => createExecution(token, rest)
    })
    .toRoutes

  val addResultsRoutes =
    ExecutionEndpoints.addResults.toRoutes(Function.tupled(addExecutionResults))

  val routes: HttpRoutes[F] = detail <+> create <+> list

}
