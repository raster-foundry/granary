package com.rasterfoundry.granary.api.services

import java.util.UUID

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.colisweb.tracing.NoOpTracingContext
import com.rasterfoundry.granary.api.endpoints.DeleteMessage
import com.rasterfoundry.granary.api.error.NotFound
import com.rasterfoundry.granary.database.TestDatabaseSpec
import com.rasterfoundry.granary.datamodel._
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.scalacheck._
import org.specs2.{ScalaCheck, Specification}

class ModelServiceSpec
    extends Specification
    with ScalaCheck
    with Generators
    with Setup
    with Teardown
    with TestDatabaseSpec {

  def is = s2"""
  This specification verifies that the Model Service can run without crashing

  The model service should:
    - create models                           $createExpectation
    - get models by id                        $getByIdExpectation
    - list models                             $listModelsExpectation
    - delete models                           $deleteModelExpectation
"""

  val tracingContextBuilder = NoOpTracingContext.getNoOpTracingContextBuilder[IO].unsafeRunSync

  def service: ModelService[IO] =
    new ModelService[IO](
      PageRequest(Some(NonNegInt(0)), Some(PosInt(30))),
      tracingContextBuilder,
      transactor
    )

  def createExpectation = prop { (model: Model.Create) =>
    {
      val out = for {
        created <- createModel(model, service)
        _       <- deleteModel(created, service)
      } yield created

      out.value.unsafeRunSync.get.toCreate ==== model
    }
  }

  def getByIdExpectation = prop { (model: Model.Create) =>
    {
      val getByIdAndBogus: OptionT[IO, (Model, Response[IO], NotFound)] = for {
        decoded <- createModel(model, service)
        successfulByIdRaw <- service.routes.run(
          Request[IO](
            method = Method.GET,
            uri = Uri.fromString(s"/models/${decoded.id}").right.get
          )
        )
        successfulById <- OptionT.liftF { successfulByIdRaw.as[Model] }
        missingByIdRaw <- service.routes.run(
          Request[IO](
            method = Method.GET,
            uri = Uri.fromString(s"/models/${UUID.randomUUID}").right.get
          )
        )
        missingById <- OptionT.liftF { missingByIdRaw.as[NotFound] }
        _           <- deleteModel(decoded, service)
      } yield { (successfulById, missingByIdRaw, missingById) }

      val (outModel, missingResp, missingBody) = getByIdAndBogus.value.unsafeRunSync.get

      outModel.toCreate ==== model && missingResp.status.code ==== 404 && missingBody ==== NotFound()

    }
  }

  def listModelsExpectation = {
    val models = Arbitrary.arbitrary[List[Model.Create]].sample.get.take(30)
    val listIO = for {
      models <- models traverse { model => createModel(model, service) }
      listedRaw <- service.routes.run(
        Request[IO](method = Method.GET, uri = Uri.uri("/models"))
      )
      listed <- OptionT.liftF { listedRaw.as[PaginatedResponse[Model]] }
      _      <- models traverse { model => deleteModel(model, service) }
    } yield (models, listed)

    val (inserted, listed) = listIO.value.unsafeRunSync.get
    listed.results.intersect(inserted).toSet == inserted.toSet
  }

  def deleteModelExpectation = prop { (model: Model.Create) =>
    {
      val deleteIO = for {
        decoded    <- createModel(model, service)
        deleteById <- deleteModel(decoded, service)
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
