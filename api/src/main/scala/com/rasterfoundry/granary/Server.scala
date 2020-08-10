package com.rasterfoundry.granary.api

import com.rasterfoundry.granary.api.auth._
import com.rasterfoundry.granary.api.options.Options
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.services._
import com.rasterfoundry.granary.database.{Config => DBConfig, DatabaseConfig}
import cats.effect._
import cats.implicits._
import com.colisweb.tracing._
import com.monovore.decline.Command
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
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

  def createServer(
      databaseConfig: DatabaseConfig,
      metaConfig: MetaConfig,
      s3Config: S3Config,
      authConfig: AuthConfig,
      paginationConfig: PaginationConfig
  ): Resource[IO, Server[IO]] =
    for {
      tracingContextBuilder <- Resource.liftF {
        getTracingContextBuilder flatMap {
          case Left(e)        => IO.raiseError(new Exception(e.toString))
          case Right(builder) => IO.pure(builder)
        }
      }
      connectionEc        <- ExecutionContexts.fixedThreadPool[IO](2)
      dbBlocker           <- Blocker[IO]
      staticAssetsBlocker <- Blocker[IO]
      transactor <-
        HikariTransactor
          .fromHikariConfig[IO](DBConfig.hikariConfig(databaseConfig), connectionEc, dbBlocker)
      auth = new Auth(authConfig, transactor)
      allEndpoints = {
        TaskEndpoints.endpoints ++ ExecutionEndpoints.endpoints ++ HealthcheckEndpoints.endpoints
      }
      docs               = allEndpoints.toOpenAPI("Granary", "0.0.1")
      docRoutes          = new SwaggerHttp4s(docs.toYaml).routes
      defaultPageRequest = PageRequest.default(paginationConfig.defaultLimit)
      taskRoutes = new TaskService(
        defaultPageRequest,
        tracingContextBuilder,
        transactor,
        auth
      ).routes

      executionService = new ExecutionService(
        defaultPageRequest,
        tracingContextBuilder,
        transactor,
        s3Config.dataBucket,
        metaConfig.apiHost,
        auth
      )
      executionRoutes   = executionService.routes
      healthcheckRoutes = new HealthcheckService(tracingContextBuilder, transactor).healthcheck
      staticRoutes      = new StaticService[IO](staticAssetsBlocker).routes
      router =
        RequestResponseLogger
          .httpRoutes(false, false) {
            CORS(
              Router(
                "/api" ->
                  (taskRoutes <+> executionRoutes <+>
                    healthcheckRoutes <+> docRoutes <+> executionService.addResultsRoutes),
                "/" -> staticRoutes
              )
            )
          }
          .orNotFound
      serverBuilderBlocker <- Blocker[IO]
      server <- BlazeServerBuilder[IO](serverBuilderBlocker.blockingContext)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(router)
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = {
    val cmd = Command(name = "Granary", header = "A validating job runner for AWS Batch") {
      (
        Options.databaseConfig,
        Options.metaConfig,
        Options.s3Config,
        Options.authConfig,
        Options.paginationConfig
      ).tupled
    }
    cmd.parse(args) map {
      case (dbConfig, metaConfig, s3Config, authConfig, paginationConfig) =>
        createServer(dbConfig, metaConfig, s3Config, authConfig, paginationConfig)
          .use(_ => IO.never)
          .as(ExitCode.Success)
    } match {
      case Right(s) => s
      case Left(err) =>
        IO {
          println(err.toString)
        } map { _ => ExitCode.Error }
    }
  }
}
