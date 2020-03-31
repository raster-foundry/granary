package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.datamodel.HealthcheckResult

import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.model.StatusCode

object HealthcheckEndpoints {

  val base = endpoint.in("healthcheck")

  val healthcheckEndpoint: Endpoint[Unit, HealthcheckResult, HealthcheckResult, Nothing] =
    base.get
      .out(jsonBody[HealthcheckResult])
      .errorOut(
        oneOf(
          statusMapping(
            StatusCode.ServiceUnavailable,
            jsonBody[HealthcheckResult].description("Upstream services unavailable")
          )
        )
      )
      .description("Check service availability")
      .name("healthcheck")

  val endpoints = List(
    healthcheckEndpoint
  )
}
