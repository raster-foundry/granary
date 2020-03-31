package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.api.endpoints.HealthcheckEndpoints
import com.rasterfoundry.granary.datamodel.{HealthResult, HealthyResult, UnhealthyResult}

import cats._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContextBuilder
import doobie.Transactor
import doobie.implicits._
import io.chrisdavenport.log4cats.Logger
import sttp.tapir.server.http4s._

import scala.concurrent.duration._

class HealthcheckService[F[_]: Sync: Logger: MonadError[*[_], Throwable]: Concurrent](
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F]
)(
    implicit contextShift: ContextShift[F],
    timer: Timer[F]
) extends GranaryService {

  def checkHealth: F[Either[UnhealthyResult, HealthyResult]] =
    mkContext("healthcheck", Map.empty, contextBuilder).use { _ =>
      Concurrent[F]
        .race(
          timer.sleep(5.seconds) map { _ => UnhealthyResult(database = HealthResult.Unhealthy) },
          fr"select 1 from models limit 1;".query[Int].option.transact(xa) map { _ =>
            HealthyResult()
          }
        )
        .attempt flatMap {
        case Right(Left(unhealthy)) =>
          Applicative[F].pure { Left(unhealthy) }
        case Right(Right(healthy)) =>
          Applicative[F].pure { Right(healthy) }
        case Left(e) =>
          Logger[F].error(e)("Database health check failed") map { _ =>
            Left(UnhealthyResult(database = HealthResult.Unhealthy))
          }
      }
    }

  val healthcheck = HealthcheckEndpoints.healthcheckEndpoint.toRoutes((_: Unit) => checkHealth)
}
