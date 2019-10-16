package com.rasterfoundry.granary.api

import io.circe._
import tapir._
import tapir.json.circe._

import scala.language.higherKinds

object HelloEndpoints {

  val base = endpoint.in("hello")

  val greetEndpoint: Endpoint[String, HelloError, Json, Nothing] =
    base.get
      .in(path[String])
      .out(jsonBody[Json])
      .errorOut(jsonBody[HelloError])
      .description("Greet someone")
      .name("greet")

  val endpoints = List(
    greetEndpoint
  )
}
