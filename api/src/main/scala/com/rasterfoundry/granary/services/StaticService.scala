package com.rasterfoundry.granary.api.services

import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl

class StaticService[F[_]: Sync](blocker: Blocker)(implicit cs: ContextShift[F])
    extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> path =>
      StaticFile
        .fromResource(s"/assets$path", blocker, Some(req))
        .orElse(StaticFile.fromResource("/assets/index.html", blocker, None))
        .getOrElseF(NotFound())
  }
}
