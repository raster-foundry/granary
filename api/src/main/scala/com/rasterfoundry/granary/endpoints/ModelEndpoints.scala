package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._
import sttp.tapir._
import sttp.tapir.json.circe._
import java.util.UUID

import sttp.model.StatusCode

object ModelEndpoints {
  val base = endpoint.in("models")

  val idLookup = base.get
    .in(path[UUID])
    .in(header[Option[String]]("Authorization"))
    .out(jsonBody[Model])
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
      jsonBody[Model.Create].description(
        "A name, a Json Schema and some AWS batch metadata for running a this model. The Schema should specify arguments as string -> string, even if they'll be parsed as ints or bools or whatever by the eventual model run"
      )
    )
    .in(header[Option[String]]("Authorization"))
    .out(jsonBody[Model])
    .errorOut(
      oneOf[CrudError](
        statusMapping(
          StatusCode.Forbidden,
          jsonBody[Forbidden].description("Invalid token")
        )
      )
    )

  val list = base.get
    .in(header[Option[String]]("Authorization"))
    .out(jsonBody[List[Model]])
    .errorOut(
      oneOf[CrudError](
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
        statusMapping(
          StatusCode.Forbidden,
          jsonBody[Forbidden].description("Invalid token")
        )
      )
    )

  val delete = base.delete
    .in(path[UUID])
    .in(header[Option[String]]("Authorization"))
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
