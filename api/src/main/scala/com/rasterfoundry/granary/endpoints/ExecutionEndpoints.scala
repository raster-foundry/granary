package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.api._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._
import sttp.tapir.{ValidationError => _, _}
import sttp.tapir.json.circe._

import java.util.UUID

import sttp.model.StatusCode

object ExecutionEndpoints {

  val base = endpoint.in("executions")

  val idLookup = base.get
    .in(path[UUID])
    .out(jsonBody[Execution])
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
      jsonBody[Execution.Create].description(
        "A model ID and arguments to use to run a execution. Arguments must conform to the schema on the associated model"
      )
    )
    .out(jsonBody[Execution])
    .errorOut(
      oneOf[CrudError](
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("Not found")),
        statusMapping(
          StatusCode.BadRequest,
          jsonBody[ValidationError]
            .description("execution arguments insufficient for running model")
        )
      )
    )

  val list =
    base.get
      .in(Inputs.paginationInput)
      .in(query[Option[UUID]]("taskId"))
      .in(query[Option[JobStatus]]("status"))
      .out(jsonBody[PaginatedResponse[Execution]])

  val addResults =
    base.post
      .in(path[UUID])
      .in("results")
      .in(path[UUID])
      .in(jsonBody[ExecutionStatusUpdate])
      .out(jsonBody[Execution])
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