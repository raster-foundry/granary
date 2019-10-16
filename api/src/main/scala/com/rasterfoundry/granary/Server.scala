package com.rasterfoundry.granary.api

import cats.data.EitherT
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.NoOpTracingContext
import com.typesafe.scalalogging.LazyLogging
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware._
import org.http4s.server.Router
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._
import tapir.openapi.OpenAPI
import tapir.openapi.circe.yaml._
import tapir.docs.openapi._
import tapir.swagger.http4s.SwaggerHttp4s

object ApiServer extends IOApp with LazyLogging {

  def getApp: EitherT[IO, ConfigReaderFailures, HttpApp[IO]] =
    EitherT {
      ConfigSource.default.at("tracing").load[TracingConfig] traverse {
        case TracingConfig(true, "JAEGER") => ???
        case TracingConfig(true, "XRAY")   => ???
        case TracingConfig(true, s) =>
          logger.warn(s"Not a recognized tracing sink: $s. Using Jaeger")
          ???
        case TracingConfig(false, _) =>
          NoOpTracingContext.getNoOpTracingContextBuilder[IO]
      }
    } map { contextBuilder =>
      implicit val tracingContextBuilder = contextBuilder

      val allEndpoints              = HelloEndpoints.endpoints
      val doc: OpenAPI              = allEndpoints.toOpenAPI("Granary", "0.0.1")
      val docRoutes: HttpRoutes[IO] = new SwaggerHttp4s(doc.toYaml).routes
      val helloRoutes               = (new HelloService).routes

      CORS(
        Router(
          "/api" -> (helloRoutes <+> docRoutes)
        )
      ).orNotFound
    }

  def run(args: List[String]): IO[ExitCode] =
    getApp.value flatMap {
      case Right(httpApp) =>
        BlazeServerBuilder[IO]
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(httpApp)
          .serve
          .compile
          .drain
          .as(ExitCode.Success)
      case Left(e) =>
        IO(println(s"Failed to initialize application: $e")).as(ExitCode.Error)
    }
}
