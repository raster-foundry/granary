package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.database.{Config => DBConfig}
import com.rasterfoundry.granary.datamodel._

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.colisweb.tracing.NoOpTracingContext
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.specs2.{ScalaCheck, Specification}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class PredictionServiceSpec
    extends Specification
    with ScalaCheck
    with Setup
    with Teardown
    with Generators {
  def is = s2"""
  This specification verifies the functionality of the prediction service

  The prediction service should:
    - respond appropriately to POSTs of different types $createPredictionExpectation
    - list filtered predictions                         $listPredictionsExpectation
    - get a prediction by id                            $getPredictionByIdExpectation
  """

  def deleteModel(model: Model): IO[Unit] = ???
  def deletePrediction: IO[Unit]          = ???

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  val transactor                    = DBConfig.nonHikariTransactor[IO]
  val tracingContextBuilder         = NoOpTracingContext.getNoOpTracingContextBuilder[IO].unsafeRunSync

  val modelService: ModelService[IO] =
    new ModelService[IO](tracingContextBuilder, transactor)

  val predictionService: PredictionService[IO] =
    new PredictionService[IO](tracingContextBuilder, transactor)

  def createPredictionExpectation = prop { (model: Model.Create, pred: Prediction.Create) =>
    {
      // Schema for {"a": 1}, generated by https://jsonschema.net/
      val jsonSchema: Json = decode[Json]("""
       | {
       |   "definitions": {},
       |   "$schema": "http://json-schema.org/draft-07/schema#",
       |   "$id": "http://example.com/root.json",
       |   "type": "object",
       |   "title": "The Root Schema",
       |   "required": [
       |     "a"
       |   ],
       |   "properties": {
       |     "a": {
       |       "$id": "#/properties/a",
       |       "type": "integer",
       |       "title": "The A Schema",
       |       "default": 0,
       |       "examples": [
       |         1
       |       ]
       |     }
       |   }
       | }
       |  """.trim.stripMargin).right.get

      val testIO = for {
        createdModel <- createModel(
          model.copy(validator = Validator(jsonSchema)),
          modelService
        )
        createAttempt404 <- predictionService.routes.run(
          Request[IO](method = Method.POST, uri = Uri.uri("/predictions")).withEntity(pred)
        )
        createAttempt400 <- predictionService.routes.run(
          Request[IO](method = Method.POST, uri = Uri.uri("/predictions"))
            .withEntity(pred.copy(modelId = createdModel.id))
        )
        createdPrediction <- createPrediction(
          Prediction.Create(modelId = createdModel.id, arguments = Map("a" -> 1).asJson),
          predictionService
        )
        _ <- deleteModel(createdModel, modelService)
      } yield {
        (createAttempt404, createAttempt400, createdPrediction)
      }

      val (resp404, resp400, created) = testIO.value.unsafeRunSync.get
      resp404.status.code ==== 404 && resp400.status.code ==== 400 && created.arguments ==== Map(
        "a" -> 1
      ).asJson
    }
  }

  def listPredictionsExpectation = prop {
    (
        model1: Model.Create,
        model2: Model.Create,
        prediction1: Prediction.Create,
        prediction2: Prediction.Create
    ) =>
      {
        val testIO = for {
          createdModel1 <- createModel(model1, modelService)
          createdModel2 <- createModel(model2, modelService)
          predictions = List(
            prediction1.copy(modelId = createdModel1.id),
            prediction2.copy(modelId = createdModel2.id)
          )
          allCreatedPreds <- predictions traverse { createPrediction(_, predictionService) }
          model1Uri = Uri.fromString(s"/predictions?modelId=${createdModel1.id}").right.get
          model2Uri = Uri.fromString(s"/predictions?modelId=${createdModel2.id}").right.get
          listedForModel1 <- predictionService.routes.run(
            Request[IO](method = Method.GET, uri = model1Uri)
          ) flatMap { resp =>
            OptionT.liftF { resp.as[List[Prediction]] }
          }
          listedForModel2 <- predictionService.routes.run(
            Request[IO](method = Method.GET, uri = model2Uri)
          ) flatMap { resp =>
            OptionT.liftF { resp.as[List[Prediction]] }
          }
          _ <- deleteModel(createdModel1, modelService)
          _ <- deleteModel(createdModel2, modelService)
        } yield {
          (createdModel1.id, createdModel2.id, listedForModel1, listedForModel2, allCreatedPreds)
        }

        val (model1Id, model2Id, model1Preds, model2Preds, allCreatedPreds) =
          testIO.value.unsafeRunSync.get

        model1Preds.filter(_.modelId == model1Id) ==== model1Preds && model2Preds.filter(
          _.modelId == model2Id
        ) ==== model2Preds && (model1Preds ++ model2Preds).toSet ==== allCreatedPreds.toSet
      }
  }

  def getPredictionByIdExpectation = prop { (model: Model.Create, prediction: Prediction.Create) =>
    {
      val testIO = for {
        createdModel <- createModel(model, modelService)
        createdPrediction <- createPrediction(
          prediction.copy(modelId = createdModel.id),
          predictionService
        )
        predictionById <- predictionService.routes.run(
          Request[IO](
            method = Method.GET,
            uri = Uri.fromString(s"/predictions/${createdPrediction.id}").right.get
          )
        ) flatMap { resp =>
          OptionT.liftF { resp.as[Prediction] }
        }
        _ <- deleteModel(createdModel, modelService)
      } yield { (createdPrediction, predictionById) }

      val (createdPrediction, predictionById) = testIO.value.unsafeRunSync.get
      createdPrediction ==== predictionById
    }
  }
}
