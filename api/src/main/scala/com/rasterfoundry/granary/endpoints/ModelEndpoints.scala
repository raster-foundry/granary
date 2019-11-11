package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._

import tapir._
import tapir.json.circe._

import java.util.UUID

object ModelEndpoints {
  val base = endpoint.in("models")

  val idLookup = base.get
    .in(path[UUID])
    .out(jsonBody[Model])
    .errorOut(oneOf(statusMapping(404, jsonBody[NotFound].description("not found"))))

  val create = base.post
    .in(
      jsonBody[Model.Create].description(
        "A name, a Json Schema and some AWS batch metadata for running a this model. The Schema should specify arguments as string -> string, even if they'll be parsed as ints or bools or whatever by the eventual model run"
      )
    )
    .out(jsonBody[Model])

  val list = base.get.out(jsonBody[List[Model]])

  val delete = base.delete
    .in(path[UUID])
    .out(jsonBody[DeleteMessage])
    .errorOut(oneOf(statusMapping(404, jsonBody[NotFound])))

  val endpoints = List(idLookup, create, list, delete)
}
