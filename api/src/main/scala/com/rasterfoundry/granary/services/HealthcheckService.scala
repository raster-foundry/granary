package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.api.endpoints.HealthcheckEndpoints
import com.rasterfoundry.granary.datamodel.{HealthResult, HealthcheckResult}

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

  def checkHealth: F[Either[HealthcheckResult, HealthcheckResult]] =
    mkContext("healthcheck", Map.empty, contextBuilder).use { _ =>
      Concurrent
        .timeoutTo[F, HealthcheckResult](
          fr"select 1 from models limit 1;".query[Int].option.transact(xa) map { _ =>
            HealthcheckResult(database = HealthResult.Healthy)
          },
          5.seconds,
          Applicative[F].pure(HealthcheckResult(database = HealthResult.Unhealthy))
        )
        .attempt flatMap {
        case Right(_) =>
          Applicative[F].pure { Right(HealthcheckResult(database = HealthResult.Healthy)) }
        case Left(e) =>
          Logger[F].error(e)("Database health check failed") map { _ =>
            Left(HealthcheckResult(database = HealthResult.Unhealthy))
          }
      }
    }

  val healthcheck = HealthcheckEndpoints.healthcheckEndpoint.toRoutes((_: Unit) => checkHealth)
}
