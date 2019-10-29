package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.api._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._

import tapir.{ValidationError => _, _}
import tapir.json.circe._

import java.util.UUID

object PredictionEndpoints {

  val base = endpoint.in("predictions")

  val idLookup = base.get
    .in(path[UUID])
    .out(jsonBody[Prediction])
    .errorOut(oneOf(statusMapping(404, jsonBody[NotFound].description("not found"))))

  val create = base.post
    .in(jsonBody[Prediction.Create])
    .out(jsonBody[Prediction])
    .errorOut(
      oneOf[CrudError](
        statusMapping(404, jsonBody[NotFound].description("not found")),
        statusMapping(
          400,
          jsonBody[ValidationError]
            .description("prediction arguments insufficient for running model")
        )
      )
    )

  val list =
    base.get
      .in(query[Option[UUID]]("modelId"))
      .in(query[Option[JobStatus]]("status"))
      .out(jsonBody[List[Prediction]])

  val endpoints = List(idLookup, create, list)
}
