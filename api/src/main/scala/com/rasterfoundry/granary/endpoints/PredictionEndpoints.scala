package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.api._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._
import sttp.tapir.{ValidationError => _, _}
import sttp.tapir.json.circe._

import java.util.UUID

import sttp.model.StatusCode

object PredictionEndpoints {

  val base = endpoint.in("predictions")

  val idLookup = base.get
    .in(path[UUID])
    .out(jsonBody[Prediction])
    .errorOut(
      oneOf[CrudError](
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("Not found")),
        statusMapping(
          StatusCode.Forbidden,
          jsonBody[Forbidden].description("Invalid token")
        )
      )
    )

  val create = base.post
    .in(
      jsonBody[Prediction.Create].description(
        "A model ID and arguments to use to run a prediction. Arguments must conform to the schema on the associated model"
      )
    )
    .out(jsonBody[Prediction])
    .errorOut(
      oneOf[CrudError](
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("Not found")),
        statusMapping(
          StatusCode.BadRequest,
          jsonBody[ValidationError]
            .description("prediction arguments insufficient for running model")
        )
      )
    )

  val list =
    base.get
      .in(Inputs.paginationInput)
      .in(query[Option[UUID]]("modelId"))
      .in(query[Option[JobStatus]]("status"))
      .out(jsonBody[PaginatedResponse[Prediction]])

  val addResults =
    base.post
      .in(path[UUID])
      .in("results")
      .in(path[UUID])
      .in(jsonBody[PredictionStatusUpdate])
      .out(jsonBody[Prediction])
      .errorOut(
        oneOf[CrudError](
          statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
          statusMapping(
            StatusCode.Forbidden,
            jsonBody[Forbidden].description("Invalid token")
          )
        )
      )

  val endpoints = List(idLookup, create, list, addResults)
}
