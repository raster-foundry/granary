package com.rasterfoundry.granary.api

import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.services._
import com.rasterfoundry.granary.database.{Config => DBConfig}
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.rasterfoundry.http4s.{JaegerTracer, XRayTracer}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware._
import org.http4s.server._
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._
import tapir.openapi.circe.yaml._
import tapir.docs.openapi._
import tapir.swagger.http4s.SwaggerHttp4s

object ApiServer extends IOApp {

  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

  def getTracingContextBuilder: IO[Either[ConfigReaderFailures, TracingContextBuilder[IO]]] =
    ConfigSource.default.at("tracing").load[TracingConfig] traverse {
      case TracingConfig(s) if s.toUpperCase() == "JAEGER" =>
        Logger[IO].debug("Using jaeger tracer") map { _ =>
          JaegerTracer.tracingContextBuilder
        }
      case TracingConfig(s) if s.toUpperCase() == "XRAY" =>
        Logger[IO].debug("Using XRay tracer") map { _ =>
          XRayTracer.tracingContextBuilder
        }
      case TracingConfig(s) =>
        Logger[IO].warn(s"Not a recognized tracing sink: $s. Using Jaeger") map { _ =>
          JaegerTracer.tracingContextBuilder
        }
    }

  def createServer: Resource[IO, Server[IO]] =
    for {
      tracingContextBuilder <- Resource.liftF {
        getTracingContextBuilder flatMap {
          case Left(e)        => IO.raiseError(throw new Exception(e.toString))
          case Right(builder) => IO.pure(builder)
        }
      }
      connectionEc <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker      <- Blocker[IO]
      transactor <- HikariTransactor
        .fromHikariConfig[IO](DBConfig.hikariConfig, connectionEc, blocker)
      allEndpoints = HelloEndpoints.endpoints ++ ModelEndpoints.endpoints
      docs         = allEndpoints.toOpenAPI("Granary", "0.0.1")
      docRoutes    = new SwaggerHttp4s(docs.toYaml).routes
      helloRoutes  = new HelloService(tracingContextBuilder).routes
      modelRoutes  = new ModelService(tracingContextBuilder, transactor).routes
      router = CORS(
        Router(
          "/api" -> (helloRoutes <+> modelRoutes <+> docRoutes)
        )
      ).orNotFound
      server <- BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(router)
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] =
    createServer.use(_ => IO.never).as(ExitCode.Success)
}
