package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.database.{Config => DBConfig}
import com.rasterfoundry.granary.datamodel._
import com.rasterfoundry.granary.datamodel.Generators

import cats.data.OptionT
import cats.effect.{ContextShift, IO, Resource}
import com.colisweb.tracing.{NoOpTracingContext, TracingContext}
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.rasterfoundry.http4s.JaegerTracer
import org.http4s._
import org.http4s.circe._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._
import org.specs2.{ScalaCheck, Specification}

import scala.concurrent.ExecutionContext.Implicits.global

class ModelServiceSpec extends Specification with ScalaCheck with Generators {
  def is = s2"""
  This specification verifies that the Model Service can run without crashing

  The model service should:
    - create models                           $createExpectation
    - get models by id                        getByIdExpectation
    - return 404s for missing ids             getById404Expectation
    - list models                             listModelsExpectation
    - delete models                           deleteModelsExpectation
    - return 404s for deleting missing models deleteModels404Expectation
"""

  val transactor = DBConfig.nonHikariTransactor[IO]

  val routes: HttpRoutes[IO] =
    new ModelService[IO](JaegerTracer.tracingContextBuilder, transactor).routes

  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def createExpectation = prop { (model: Model) =>
    {
      val request =
        Request[IO](method = Method.POST, uri = Uri.uri("/models")).withEntity(model)
      val returned: OptionT[IO, Model] = for {
        resp    <- routes.run(request)
        _       <- OptionT.liftF { IO { println(s"Resp is: $resp") } }
        decoded <- OptionT.liftF { resp.as[Model] }
      } yield decoded

      returned.value.unsafeRunSync must be(Some(model))
    }
  }
}
