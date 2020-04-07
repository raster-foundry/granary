package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.datamodel.{HealthyResult, UnhealthyResult}

import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.model.StatusCode
import com.rasterfoundry.granary.datamodel.UnhealthyResult

object HealthcheckEndpoints {

  val base = endpoint.in("healthcheck")

  val healthcheckEndpoint: Endpoint[Unit, UnhealthyResult, HealthyResult, Nothing] =
    base.get
      .out(jsonBody[HealthyResult])
      .errorOut(
        oneOf(
          statusMapping(
            StatusCode.ServiceUnavailable,
            jsonBody[UnhealthyResult].description("Upstream services unavailable")
          )
        )
      )
      .description("Check service availability")
      .name("healthcheck")

  val endpoints = List(
    healthcheckEndpoint
  )
}
