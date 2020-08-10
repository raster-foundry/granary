package com.rasterfoundry.granary.api.services

import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl

class StaticService[F[_]: Sync](blocker: Blocker)(implicit cs: ContextShift[F])
    extends Http4sDsl[F] {

  private val indexHtmlPath = "/assets/index.html"

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> Root =>
      StaticFile.fromResource(indexHtmlPath, blocker, Some(req)).getOrElseF(NotFound())

    case req @ GET -> path =>
      StaticFile
        .fromResource(s"/assets$path", blocker, Some(req))
        .orElse(StaticFile.fromResource(indexHtmlPath, blocker, None))
        .getOrElseF(NotFound())
  }
}
