package com.rasterfoundry.granary.api.services

import cats._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.datamodel.{HealthResult, HealthcheckResult}
import doobie.Transactor
import doobie.implicits._
import sttp.tapir.server.http4s._

import com.rasterfoundry.granary.api.endpoints.HealthcheckEndpoints
import io.chrisdavenport.log4cats.Logger

class HealthcheckService[F[_]: Sync: Logger: MonadError[*[_], Throwable]](
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F]
)(
    implicit contextShift: ContextShift[F]
) extends GranaryService {

  def checkHealth: F[Either[Unit, HealthcheckResult]] =
    mkContext("healthcheck", Map.empty, contextBuilder).use { _ =>
      fr"select 1 from models limit 1;".query[Int].option.transact(xa).attempt flatMap {
        case Right(_) =>
          Applicative[F].pure { Right(HealthcheckResult(database = HealthResult.Healthy)) }
        case Left(e) =>
          Logger[F].error(e)("Database health check failed") map { _ =>
            Right(HealthcheckResult(database = HealthResult.Unhealthy))
          }
      }
    }

  val healthcheck = HealthcheckEndpoints.healthcheckEndpoint.toRoutes((_: Unit) => checkHealth)
}
