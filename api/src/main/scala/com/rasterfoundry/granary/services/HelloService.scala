package com.rasterfoundry.granary.api.services

import cats._
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import cats.effect._
import com.colisweb.tracing.TracingContextBuilder
import io.circe._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tapir.server.http4s._

class HelloService[F[_]: Sync](contextBuilder: TracingContextBuilder[F])(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F]
    with GranaryService {

  def greet(name: String): F[Either[HelloError, Json]] = {
    mkContext("greet", Map("name" -> name), contextBuilder) use { _ =>
      name match {
        case "throwme" => Applicative[F].pure { Left(HelloError("Oh no an invalid input")) }
        case s =>
          Applicative[F].pure { Right(Json.obj("message" -> Json.fromString(s"Hello, $s"))) }
      }
    }
  }

  val routes: HttpRoutes[F] = HelloEndpoints.greetEndpoint.toRoutes(greet _)
}
