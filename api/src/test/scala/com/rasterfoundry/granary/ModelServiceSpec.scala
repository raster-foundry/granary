package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.api.endpoints.DeleteMessage
import com.rasterfoundry.granary.api.error.NotFound

import com.rasterfoundry.granary.database.{Config => DBConfig}
import com.rasterfoundry.granary.datamodel._

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.colisweb.tracing.NoOpTracingContext
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.scalacheck._
import org.specs2.{ScalaCheck, Specification}

import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID

class ModelServiceSpec extends Specification with ScalaCheck with Generators with Setup {
  def is = s2"""
  This specification verifies that the Model Service can run without crashing

  The model service should:
    - create models                           $createExpectation
    - get models by id                        $getByIdExpectation
    - list models                             $listModelsExpectation
    - delete models                           $deleteModelExpectation
"""

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  val transactor                    = DBConfig.nonHikariTransactor[IO]
  val tracingContextBuilder         = NoOpTracingContext.getNoOpTracingContextBuilder[IO].unsafeRunSync

  val service: ModelService[IO] =
    new ModelService[IO](tracingContextBuilder, transactor)

  def createExpectation = prop { (model: Model) =>
    {
      val out: Model = createModel(model, service).value.unsafeRunSync.get

      out ==== model
    }
  }

  def getByIdExpectation = prop { (model: Model) =>
    {
      val getByIdAndBogus: OptionT[IO, (Model, Response[IO], NotFound)] = for {
        decoded <- createModel(model, service)
        successfulByIdRaw <- service.routes.run(
          Request[IO](method = Method.GET, uri = Uri.fromString(s"/models/${decoded.id}").right.get)
        )
        successfulById <- OptionT.liftF { successfulByIdRaw.as[Model] }
        missingByIdRaw <- service.routes.run(
          Request[IO](
            method = Method.GET,
            uri = Uri.fromString(s"/models/${UUID.randomUUID}").right.get
          )
        )
        missingById <- OptionT.liftF { missingByIdRaw.as[NotFound] }
      } yield { (successfulById, missingByIdRaw, missingById) }

      val (outModel, missingResp, missingBody) = getByIdAndBogus.value.unsafeRunSync.get

      outModel ==== model && missingResp.status.code ==== 404 && missingBody ==== NotFound()

    }
  }

  def listModelsExpectation = {
    val models = Arbitrary.arbitrary[List[Model]].sample.get
    val listIO = for {
      models <- models traverse { model =>
        createModel(model, service)
      }
      listedRaw <- service.routes.run(
        Request[IO](method = Method.GET, uri = Uri.uri("/models"))
      )
      listed <- OptionT.liftF { listedRaw.as[List[Model]] }
    } yield (models, listed)

    val (inserted, listed) = listIO.value.unsafeRunSync.get
    listed.contains(inserted)
  }

  def deleteModelExpectation = prop { (model: Model) =>
    {
      val deleteIO = for {
        decoded <- createModel(model, service)
        deleteByIdRaw <- service.routes.run(
          Request[IO](
            method = Method.DELETE,
            uri = Uri.fromString(s"/models/${decoded.id}").right.get
          )
        )
        deleteById <- OptionT.liftF { deleteByIdRaw.as[DeleteMessage] }
        missingByIdRaw <- service.routes.run(
          Request[IO](
            method = Method.DELETE,
            uri = Uri.fromString(s"/models/${UUID.randomUUID}").right.get
          )
        )
        missingById <- OptionT.liftF { missingByIdRaw.as[NotFound] }
      } yield { (deleteById, missingByIdRaw, missingById) }

      val (outDeleted, missingResp, missingBody) =
        deleteIO.value.unsafeRunSync.get

      outDeleted ==== DeleteMessage(1) && missingResp.status.code ==== 404 && missingBody ==== NotFound()
    }
  }
}
