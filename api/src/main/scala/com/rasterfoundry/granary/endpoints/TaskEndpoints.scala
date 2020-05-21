package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._

import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.model.StatusCode

import java.util.UUID

object TaskEndpoints {

  val base = endpoint.in("tasks")

  val idLookup = base.get
    .in(path[UUID])
    .out(jsonBody[Task])
    .errorOut(
      oneOf[CrudError](
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
        statusMapping(
          StatusCode.Forbidden,
          jsonBody[Forbidden].description("Invalid token")
        )
      )
    )

  val create = base.post
    .in(
      jsonBody[Task.Create].description(
        "A name, a Json Schema and some AWS batch metadata for running a this model. The Schema should specify arguments as string -> string, even if they'll be parsed as ints or bools or whatever by the eventual model run"
      )
    )
    .out(jsonBody[Task])

  val list = base.get
    .in(Inputs.paginationInput)
    .out(jsonBody[PaginatedResponse[Task]])

  val delete = base.delete
    .in(path[UUID])
    .out(jsonBody[DeleteMessage])
    .errorOut(
      oneOf[CrudError](
        statusMapping(StatusCode.NotFound, jsonBody[NotFound]),
        statusMapping(
          StatusCode.Forbidden,
          jsonBody[Forbidden].description("Invalid token")
        )
      )
    )

  val endpoints = List(idLookup, create, list, delete)
}
