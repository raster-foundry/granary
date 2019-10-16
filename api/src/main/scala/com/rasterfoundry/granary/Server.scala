package com.rasterfoundry.granary.api

import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.services._

import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.rasterfoundry.http4s.{JaegerTracer, XRayTracer}
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

  def getApp: Either[ConfigReaderFailures, HttpApp[IO]] =
    ConfigSource.default.at("tracing").load[TracingConfig] map {
      case TracingConfig(s) if s.toUpperCase() == "JAEGER" =>
        JaegerTracer.tracingContextBuilder
      case TracingConfig(s) if s.toUpperCase() == "XRAY" =>
        XRayTracer.tracingContextBuilder
      case TracingConfig(s) =>
        logger.warn(s"Not a recognized tracing sink: $s. Using Jaeger")
        JaegerTracer.tracingContextBuilder
    } map { (contextBuilder: TracingContextBuilder[IO]) =>
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
    getApp match {
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
