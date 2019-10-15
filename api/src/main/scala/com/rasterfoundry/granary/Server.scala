package com.rasterfoundry.granary.api

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware._
import org.http4s.server.Router
import tapir.openapi.OpenAPI
import tapir.openapi.circe.yaml._
import tapir.docs.openapi._
import tapir.swagger.http4s.SwaggerHttp4s

object ApiServer extends IOApp {

  private val allEndpoints      = HelloEndpoints.endpoints
  val doc: OpenAPI              = allEndpoints.toOpenAPI("Granary", "0.0.1")
  val docRoutes: HttpRoutes[IO] = new SwaggerHttp4s(doc.toYaml).routes
  val helloRoutes               = (new HelloService).routes

  val httpApp: HttpApp[IO] = CORS(
    Router(
      "/api" -> (helloRoutes <+> docRoutes)
    )
  ).orNotFound

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
