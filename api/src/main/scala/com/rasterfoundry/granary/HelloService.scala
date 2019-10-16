package com.rasterfoundry.granary.api

import cats.effect._
import com.colisweb.tracing.TracingContextBuilder
import io.circe._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tapir.server.http4s._

class HelloService(
    implicit contextShift: ContextShift[IO],
    contextBuilder: TracingContextBuilder[IO]
) extends Http4sDsl[IO] {

  def greet(name: String): IO[Either[HelloError, Json]] = {
    contextBuilder("greet", Map("name" -> name)) use { _ =>
      name match {
        case "throwme" => IO.pure { Left(HelloError("Oh no an invalid input")) }
        case s         => IO.pure { Right(Json.obj("message" -> Json.fromString(s"Hello, $s"))) }
      }
    }
  }

  val routes: HttpRoutes[IO] = HelloEndpoints.greetEndpoint.toRoutes(greet _)
}
