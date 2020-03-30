package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.datamodel.HealthcheckResult

import sttp.tapir._
import sttp.tapir.json.circe._

object HealthcheckEndpoints {

  val base = endpoint.in("healthcheck")

  val healthcheckEndpoint: Endpoint[Unit, Unit, HealthcheckResult, Nothing] =
    base.get
      .out(jsonBody[HealthcheckResult])
      .description("Check service availability")
      .name("healthcheck")

  val endpoints = List(
    healthcheckEndpoint
  )
}
