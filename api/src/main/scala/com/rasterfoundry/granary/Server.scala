package com.rasterfoundry.granary.api

import com.rasterfoundry.granary.api.middleware._
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.services._
import com.rasterfoundry.granary.database.{Config => DBConfig}
import cats.effect._
import cats.implicits._
import com.colisweb.tracing._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import eu.timepit.refined.pureconfig._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware.{Logger => RequestResponseLogger, _}
import org.http4s.server._
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.docs.openapi._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import com.rasterfoundry.granary.datamodel.PageRequest

object ApiServer extends IOApp {

  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

  def getTracingContextBuilder: IO[Either[ConfigReaderFailures, TracingContextBuilder[IO]]] =
    ConfigSource.default.at("tracing").load[TracingConfig] traverse {
      case TracingConfig(s) => {
        Logger[IO].info(s"Ignoring tracer $s due to a large tower of dependency Jenga") flatMap {
          _ => NoOpTracingContext.getNoOpTracingContextBuilder[IO]
        }
      }
    }

  def createServer: Resource[IO, Server[IO]] =
    for {
      tracingContextBuilder <- Resource.liftF {
        getTracingContextBuilder flatMap {
          case Left(e)        => IO.raiseError(new Exception(e.toString))
          case Right(builder) => IO.pure(builder)
        }
      }
      s3Config <- Resource.liftF {
        IO(ConfigSource.default.at("s3").loadOrThrow[S3Config])
      }
      metaConfig <- Resource.liftF {
        IO(ConfigSource.default.at("meta").loadOrThrow[MetaConfig])
      }
      authConfig <- Resource.liftF {
        IO(ConfigSource.default.at("auth").loadOrThrow[AuthConfig])
      }
      paginationConfig <- Resource.liftF {
        IO(ConfigSource.default.at("pagination").loadOrThrow[PaginationConfig])
      }
      connectionEc <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker      <- Blocker[IO]
      transactor <-
        HikariTransactor
          .fromHikariConfig[IO](DBConfig.hikariConfig, connectionEc, blocker)
      allEndpoints = {
        TaskEndpoints.endpoints ++ PredictionEndpoints.endpoints ++ HealthcheckEndpoints.endpoints
      }
      docs               = allEndpoints.toOpenAPI("Granary", "0.0.1")
      docRoutes          = new SwaggerHttp4s(docs.toYaml).routes
      defaultPageRequest = PageRequest.default(paginationConfig.defaultLimit)
      taskRoutes = new TaskService(
        defaultPageRequest,
        tracingContextBuilder,
        transactor
      ).routes
      predictionService = new PredictionService(
        defaultPageRequest,
        tracingContextBuilder,
        transactor,
        s3Config.dataBucket,
        metaConfig.apiHost
      )
      predictionRoutes  = predictionService.routes
      healthcheckRoutes = new HealthcheckService(tracingContextBuilder, transactor).healthcheck
      router =
        RequestResponseLogger
          .httpRoutes(false, false) {
            CORS(
              Router(
                "/api" -> ((
                  Auth.customAuthMiddleware(
                    taskRoutes <+> predictionRoutes,
                    healthcheckRoutes <+> docRoutes,
                    authConfig,
                    transactor
                  )
                ) <+> predictionService.addResultsRoutes)
              )
            )
          }
          .orNotFound
      server <-
        BlazeServerBuilder[IO]
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(router)
          .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] =
    createServer.use(_ => IO.never).as(ExitCode.Success)
}
