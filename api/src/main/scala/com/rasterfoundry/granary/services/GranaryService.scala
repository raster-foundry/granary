package com.rasterfoundry.granary.api.services

import cats.effect.Resource
import com.colisweb.tracing.{TracingContext, TracingContextBuilder}

import scala.language.higherKinds

trait GranaryService {
  // The service tag is required for Jaeger tracer to separate calls correctly
  // It otherwise defaults to raster-foundry
  private val baseTags: Map[String, String] = Map("service" -> "granary")

  def mkContext[F[_]](
      operationName: String,
      tags: Map[String, String],
      builder: TracingContextBuilder[F]
  ): Resource[F, TracingContext[F]] =
    builder(operationName, tags ++ baseTags)
}
