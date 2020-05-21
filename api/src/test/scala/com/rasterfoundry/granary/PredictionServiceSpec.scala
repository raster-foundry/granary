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

class PredictionServiceSpec
    extends Specification
    with ScalaCheck
    with Setup
    with Teardown
    with Generators
    with TestDatabaseSpec {
  def is = sequential ^ s2"""
  This specification verifies the functionality of the prediction service

  The prediction service should:
    - respond appropriately to POSTs of different types $createPredictionExpectation
    - list filtered predictions                         $listPredictionsExpectation
    - get a prediction by id                            $getPredictionByIdExpectation
    - store prediction results only when expected to    $addPredictionResultsExpectation
  """

  val tracingContextBuilder = NoOpTracingContext.getNoOpTracingContextBuilder[IO].unsafeRunSync

  val taskService: TaskService[IO] =
    new TaskService[IO](
      PageRequest(Some(NonNegInt(0)), Some(PosInt(30))),
      tracingContextBuilder,
      transactor
    )

  val predictionService =
    new PredictionService[IO](
      PageRequest(Some(NonNegInt(0)), Some(PosInt(30))),
      tracingContextBuilder,
      transactor,
      dataBucket,
      "http://localhost:8080/api"
    )

  def updatePredictionRaw(
      message: PredictionStatusUpdate,
      prediction: Prediction,
      webhookId: UUID
  ): OptionT[IO, Response[IO]] =
    predictionService.addResultsRoutes.run(
      Request[IO](
        method = Method.POST,
        uri = Uri
          .fromString(
            s"/predictions/${prediction.id}/results/${webhookId}"
          )
          .right
          .get
      ).withEntity(message)
    )

  def updatePrediction[T: Decoder](
      message: PredictionStatusUpdate,
      prediction: Prediction,
      webhookId: UUID
  ): OptionT[IO, T] =
    updatePredictionRaw(message, prediction, webhookId) flatMap { resp =>
      OptionT.liftF { resp.as[T] }
    }

  def createPredictionExpectation =
    prop { (task: Task.Create, pred: Prediction.Create) =>
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
          createAttempt404 <- predictionService.routes.run(
            Request[IO](method = Method.POST, uri = Uri.uri("/predictions")).withEntity(pred)
          )
          createAttempt400 <- predictionService.routes.run(
            Request[IO](method = Method.POST, uri = Uri.uri("/predictions"))
              .withEntity(pred.copy(taskId = createdTask.id))
          )
          createdPrediction <- createPrediction(
            Prediction.Create(taskId = createdTask.id, arguments = Map("a" -> "1").asJson),
            predictionService
          )
          _ <- deleteTask(createdTask, taskService)
        } yield {
          (createAttempt404, createAttempt400, createdPrediction)
        }

        val (resp404, resp400, created) = testIO.value.unsafeRunSync.get
        resp404.status.code ==== 404 && resp400.status.code ==== 400 && created.arguments ==== Map(
          "a" -> "1"
        ).asJson
      }
    }

  def listPredictionsExpectation =
    prop {
      (
          task1: Task.Create,
          task2: Task.Create,
          prediction1: Prediction.Create,
          prediction2: Prediction.Create
      ) =>
        {
          val testIO = for {
            createdTask1 <- createTask(task1, taskService)
            createdTask2 <- createTask(task2, taskService)
            predictions = List(
              prediction1.copy(taskId = createdTask1.id),
              prediction2.copy(taskId = createdTask2.id)
            )
            allCreatedPreds <- predictions traverse { createPrediction(_, predictionService) }
            task1Uri = Uri.fromString(s"/predictions?taskId=${createdTask1.id}").right.get
            task2Uri = Uri.fromString(s"/predictions?taskId=${createdTask2.id}").right.get
            listedForTask1 <- predictionService.routes.run(
              Request[IO](method = Method.GET, uri = task1Uri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Prediction]] } }
            listedForTask2 <- predictionService.routes.run(
              Request[IO](method = Method.GET, uri = task2Uri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Prediction]] } }
            _ <- List(
              PredictionSuccess(
                List(StacItemAsset("s3://center/of/the/universe.geojson", None, None, Nil, None))
              ),
              PredictionFailure("wasn't set up to succeed")
            ).zip(allCreatedPreds) traverse {
              case (msg, prediction) =>
                updatePrediction[Prediction](msg, prediction, prediction.webhookId.get)
            }
            successUri = Uri.fromString(s"/predictions?status=successful").right.get
            failureUri = Uri.fromString(s"/predictions?status=failed").right.get
            listedForSuccess <- predictionService.routes.run(
              Request[IO](method = Method.GET, uri = successUri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Prediction]] } }
            listedForFailure <- predictionService.routes.run(
              Request[IO](method = Method.GET, uri = failureUri)
            ) flatMap { resp => OptionT.liftF { resp.as[PaginatedResponse[Prediction]] } }
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

  def getPredictionByIdExpectation =
    prop { (task: Task.Create, prediction: Prediction.Create) =>
      {
        val testIO = for {
          createdTask <- createTask(task, taskService)
          createdPrediction <- createPrediction(
            prediction.copy(taskId = createdTask.id),
            predictionService
          )
          predictionById <- predictionService.routes.run(
            Request[IO](
              method = Method.GET,
              uri = Uri.fromString(s"/predictions/${createdPrediction.id}").right.get
            )
          ) flatMap { resp => OptionT.liftF { resp.as[Prediction] } }
          _ <- deleteTask(createdTask, taskService)
        } yield { (createdPrediction, predictionById) }

        val (createdPrediction, predictionById) = testIO.value.unsafeRunSync.get
        createdPrediction ==== predictionById
      }
    }

  def addPredictionResultsExpectation =
    prop { (task: Task.Create, prediction: Prediction.Create) =>
      {
        val testIO = for {
          createdTask <- createTask(task, taskService)
          createdPrediction <- createPrediction(
            prediction.copy(taskId = createdTask.id),
            predictionService
          )
          message =
            if (Random.nextFloat > 0.5)
              PredictionSuccess(List(StacItemAsset("s3://foo/bar.geojson", None, None, Nil, None)))
            else PredictionFailure("task failed sorry sorry sorry")
          update1 <- updatePrediction[Prediction](
            message,
            createdPrediction,
            createdPrediction.webhookId.get
          )
          // Do it again, with the same webhook id
          update2 <- updatePredictionRaw(
            message,
            createdPrediction,
            createdPrediction.webhookId.get
          )
          update2Body <- OptionT.liftF { update2.as[NotFound] }
          // Do it again, but with a random ID for the webhook
          update3 <- updatePredictionRaw(
            message,
            createdPrediction,
            UUID.randomUUID
          )
          update3Body <- OptionT.liftF { update3.as[NotFound] }
          _           <- deleteTask(createdTask, taskService)
        } yield { (message, update1, update2, update2Body, update3, update3Body) }

        val (msg, successfulUpdate, conflictUpdateResp, _, noWebhookUpdateResp, _) =
          testIO.value.unsafeRunSync.get

        val updateExpectation =
          msg match {
            case PredictionSuccess(_) =>
              List(
                successfulUpdate.results.headOption !=== None,
                successfulUpdate.status ==== JobStatus.Successful,
                successfulUpdate.statusReason ==== None
              )
            case PredictionFailure(_) =>
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
