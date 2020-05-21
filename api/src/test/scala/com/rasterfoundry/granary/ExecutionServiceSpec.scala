package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.api.error.NotFound
import com.rasterfoundry.granary.database.TestDatabaseSpec
import com.rasterfoundry.granary.datamodel._

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.azavea.stac4s.StacItemAsset
import com.colisweb.tracing.NoOpTracingContext
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.specs2.{ScalaCheck, Specification}

import scala.util.Random

import java.util.UUID

class ExecutionServiceSpec
    extends Specification
    with ScalaCheck
    with Setup
    with Teardown
    with Generators
    with TestDatabaseSpec {
  def is = sequential ^ s2"""
  This specification verifies the functionality of the execution service

  The execution service should:
    - respond appropriately to POSTs of different types $createExecutionExpectation
    - list filtered executions                         $listExecutionsExpectation
    - get a execution by id                            $getExecutionByIdExpectation
    - store execution results only when expected to    $addExecutionResultsExpectation
  """

  val tracingContextBuilder = NoOpTracingContext.getNoOpTracingContextBuilder[IO].unsafeRunSync

  val taskService: TaskService[IO] =
    new TaskService[IO](
      PageRequest(Some(NonNegInt(0)), Some(PosInt(30))),
      tracingContextBuilder,
      transactor
    )

  val executionService =
    new ExecutionService[IO](
      PageRequest(Some(NonNegInt(0)), Some(PosInt(30))),
      tracingContextBuilder,
      transactor,
      dataBucket,
      "http://localhost:8080/api"
    )

  def updateExecutionRaw(
      message: ExecutionStatusUpdate,
      execution: Execution,
      webhookId: UUID
  ): OptionT[IO, Response[IO]] =
    executionService.addResultsRoutes.run(
      Request[IO](
        method = Method.POST,
        uri = Uri
          .fromString(
            s"/executions/${execution.id}/results/${webhookId}"
          )
          .right
          .get
      ).withEntity(message)
    )

  def updateExecution[T: Decoder](
      message: ExecutionStatusUpdate,
      execution: Execution,
      webhookId: UUID
  ): OptionT[IO, T] =
    updateExecutionRaw(message, execution, webhookId) flatMap { resp =>
      OptionT.liftF { resp.as[T] }
    }

  def createExecutionExpectation =
    prop { (task: Task.Create, pred: Execution.Create) =>
      {
        // Schema for {"a": 1}, generated by https://jsonschema.net/
        val jsonSchema: Json = decode[Json]("""
       |{
       |  "definitions": {},
       |  "$schema": "http://json-schema.org/draft-07/schema#",
       |  "$id": "http://example.com/root.json",
       |  "type": "object",
       |  "title": "The Root Schema",
       |  "required": [
       |    "a"
       |  ],
       |  "properties": {
       |    "a": {
       |      "$id": "#/properties/a",
       |      "type": "string",
       |      "title": "The A Schema",
       |      "default": "",
       |      "examples": [
       |        "1"
       |      ],
       |      "pattern": "^(.*)$"
       |    }
       |  }
       |}""".trim.stripMargin).right.get

        val testIO = for {
          createdTask <- createTask(
            task.copy(validator = Validator(jsonSchema)),
            taskService
          )
          createAttempt404 <- executionService.routes.run(
            Request[IO](method = Method.POST, uri = Uri.uri("/executions")).withEntity(pred)
          )
          createAttempt400 <- executionService.routes.run(
            Request[IO](method = Method.POST, uri = Uri.uri("/executions"))
              .withEntity(pred.copy(taskId = createdTask.id))
          )
          createdExecution <- createExecution(
            Execution.Create(taskId = createdTask.id, arguments = Map("a" -> "1").asJson),
            executionService
          )
          _ <- deleteTask(createdTask, taskService)
        } yield {
          (createAttempt404, createAttempt400, createdExecution)
        }

        val (resp404, resp400, created) = testIO.value.unsafeRunSync.get
        resp404.status.code ==== 404 && resp400.status.code ==== 400 && created.arguments ==== Map(
          "a" -> "1"
        ).asJson
      }
    }

  def listExecutionsExpectation =
    prop {
      (
          task1: Task.Create,
          task2: Task.Create,
          execution1: Execution.Create,
          execution2: Execution.Create
      ) =>
        {
          val testIO = for {
            createdTask1 <- createTask(task1, taskService)
            createdTask2 <- createTask(task2, taskService)
            executions = List(
              execution1.copy(taskId = createdTask1.id),
              execution2.copy(taskId = createdTask2.id)
            )
            allCreatedPreds <- executions traverse { createExecution(_, executionService) }
            task1Uri = Uri.fromString(s"/executions?taskId=${createdTask1.id}").right.get
            task2Uri = Uri.fromString(s"/executions?taskId=${createdTask2.id}").right.get
            listedForTask1 <- executionService.routes.run(
              Request[IO](method = Method.GET, uri = task1Uri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Execution]] } }
            listedForTask2 <- executionService.routes.run(
              Request[IO](method = Method.GET, uri = task2Uri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Execution]] } }
            _ <- List(
              ExecutionSuccess(
                List(StacItemAsset("s3://center/of/the/universe.geojson", None, None, Nil, None))
              ),
              ExecutionFailure("wasn't set up to succeed")
            ).zip(allCreatedPreds) traverse {
              case (msg, execution) =>
                updateExecution[Execution](msg, execution, execution.webhookId.get)
            }
            successUri = Uri.fromString(s"/executions?status=successful").right.get
            failureUri = Uri.fromString(s"/executions?status=failed").right.get
            listedForSuccess <- executionService.routes.run(
              Request[IO](method = Method.GET, uri = successUri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Execution]] } }
            listedForFailure <- executionService.routes.run(
              Request[IO](method = Method.GET, uri = failureUri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Execution]] } }
            _ <- deleteTask(createdTask1, taskService)
            _ <- deleteTask(createdTask2, taskService)
          } yield {
            (
              createdTask1.id,
              createdTask2.id,
              listedForTask1,
              listedForTask2,
              allCreatedPreds,
              listedForSuccess,
              listedForFailure
            )
          }

          val (
            task1Id,
            task2Id,
            task1Preds,
            task2Preds,
            allCreatedPreds,
            successResults,
            failureResults
          ) =
            testIO.value.unsafeRunSync.get

          task1Preds.results
            .filter(_.taskId == task1Id) ==== task1Preds.results && task2Preds.results
            .filter(
              _.taskId == task2Id
            ) ==== task2Preds.results && (task1Preds.results ++ task2Preds.results).toSet ==== allCreatedPreds.toSet &&
          (successResults.results map { _.status }) ==== (successResults.results map { _ =>
            JobStatus.Successful
          }) && (failureResults.results map {
            _.status
          }) ==== (failureResults.results map { _ => JobStatus.Failed })
        }
    }

  def getExecutionByIdExpectation =
    prop { (task: Task.Create, execution: Execution.Create) =>
      {
        val testIO = for {
          createdTask <- createTask(task, taskService)
          createdExecution <- createExecution(
            execution.copy(taskId = createdTask.id),
            executionService
          )
          executionById <- executionService.routes.run(
            Request[IO](
              method = Method.GET,
              uri = Uri.fromString(s"/executions/${createdExecution.id}").right.get
            )
          ) flatMap { resp => OptionT.liftF { resp.as[Execution] } }
          _ <- deleteTask(createdTask, taskService)
        } yield { (createdExecution, executionById) }

        val (createdExecution, executionById) = testIO.value.unsafeRunSync.get
        createdExecution ==== executionById
      }
    }

  def addExecutionResultsExpectation =
    prop { (task: Task.Create, execution: Execution.Create) =>
      {
        val testIO = for {
          createdTask <- createTask(task, taskService)
          createdExecution <- createExecution(
            execution.copy(taskId = createdTask.id),
            executionService
          )
          message =
            if (Random.nextFloat > 0.5)
              ExecutionSuccess(List(StacItemAsset("s3://foo/bar.geojson", None, None, Nil, None)))
            else ExecutionFailure("task failed sorry sorry sorry")
          update1 <- updateExecution[Execution](
            message,
            createdExecution,
            createdExecution.webhookId.get
          )
          // Do it again, with the same webhook id
          update2 <- updateExecutionRaw(
            message,
            createdExecution,
            createdExecution.webhookId.get
          )
          update2Body <- OptionT.liftF { update2.as[NotFound] }
          // Do it again, but with a random ID for the webhook
          update3 <- updateExecutionRaw(
            message,
            createdExecution,
            UUID.randomUUID
          )
          update3Body <- OptionT.liftF { update3.as[NotFound] }
          _           <- deleteTask(createdTask, taskService)
        } yield { (message, update1, update2, update2Body, update3, update3Body) }

        val (msg, successfulUpdate, conflictUpdateResp, _, noWebhookUpdateResp, _) =
          testIO.value.unsafeRunSync.get

        val updateExpectation =
          msg match {
            case ExecutionSuccess(_) =>
              List(
                successfulUpdate.results.headOption !=== None,
                successfulUpdate.status ==== JobStatus.Successful,
                successfulUpdate.statusReason ==== None
              )
            case ExecutionFailure(_) =>
              List(
                successfulUpdate.results.headOption ==== None,
                successfulUpdate.status ==== JobStatus.Failed,
                successfulUpdate.statusReason !=== None
              )
          }

        conflictUpdateResp.status.code ==== 404 &&
        noWebhookUpdateResp.status.code ==== 404 &&
        updateExpectation
      }
    }
}