package com.rasterfoundry.granary.api

import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.services._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.rasterfoundry.http4s.{JaegerTracer, XRayTracer}
import com.typesafe.scalalogging.LazyLogging
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
import cats.effect.implicits._

object ApiServer extends IOApp with LazyLogging {

  def getTracingContextBuilder: Either[ConfigReaderFailures, TracingContextBuilder[IO]] =
    ConfigSource.default.at("tracing").load[TracingConfig] map {
      case TracingConfig(s) if s.toUpperCase() == "JAEGER" =>
        JaegerTracer.tracingContextBuilder
      case TracingConfig(s) if s.toUpperCase() == "XRAY" =>
        XRayTracer.tracingContextBuilder
      case TracingConfig(s) =>
        logger.warn(s"Not a recognized tracing sink: $s. Using Jaeger")
        JaegerTracer.tracingContextBuilder
    }

  def createServer: Resource[IO, Server[IO]] =
    for {
      tracingContextBuilder <- Resource.liftF {
        getTracingContextBuilder match {
          case Left(e)        => IO.raiseError(throw new Exception(e.toString))
          case Right(builder) => IO.pure(builder)
        }
      }
      allEndpoints = HelloEndpoints.endpoints
      docs         = allEndpoints.toOpenAPI("Granary", "0.0.1")
      docRoutes    = new SwaggerHttp4s(docs.toYaml).routes
      helloRoutes  = new HelloService(tracingContextBuilder).routes
      router = CORS(
        Router(
          "/api" -> (helloRoutes <+> docRoutes)
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
