package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.database.TestDatabaseSpec
import com.rasterfoundry.granary.datamodel._

import cats.data.OptionT
import cats.effect.IO
import com.colisweb.tracing.NoOpTracingContext
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.specs2.{ScalaCheck, Specification}

class HealthcheckServiceSpec
    extends Specification
    with ScalaCheck
    with Setup
    with Teardown
    with TestDatabaseSpec {

  def is = s2"""
  This specification verifies that the Healthcheck Service returns appropriate results

  The healthcheck service should:
    - return 200 with a healthy database in good times    $healthyExpectation
    - return 503 with an unhealthy database in bad times  $unhealthyExpectation
"""

  implicit val unsafeLogger = Slf4jLogger.getLogger[IO]

  val tracingContextBuilder = NoOpTracingContext.getNoOpTracingContextBuilder[IO].unsafeRunSync

  def service: HealthcheckService[IO] =
    new HealthcheckService[IO](tracingContextBuilder, transactor)

  def serviceButSleepy: HealthcheckService[IO] =
    new HealthcheckService[IO](tracingContextBuilder, sleepyTransactor)

  val request = Request[IO](method = Method.GET, uri = Uri.uri("/healthcheck"))

  def healthyExpectation = {
    val io = for {
      resp    <- service.healthcheck.run(request)
      decoded <- OptionT.liftF { resp.as[HealthyResult] }
    } yield (resp, decoded)
    val (response, result) = io.value.unsafeRunSync.get

    response.status.code ==== 200 &&
    result ==== HealthyResult()
  }

  def unhealthyExpectation = {
    val io = for {
      resp    <- serviceButSleepy.healthcheck.run(request)
      decoded <- OptionT.liftF { resp.as[UnhealthyResult] }
    } yield (resp, decoded)

    val (response, result) = io.value.unsafeRunSync.get
    result.database ==== HealthResult.Unhealthy &&
    response.status.code ==== 503
  }
}
