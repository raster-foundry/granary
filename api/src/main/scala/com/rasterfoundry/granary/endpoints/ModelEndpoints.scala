package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._

import tapir._
import tapir.json.circe._

import scala.language.higherKinds

import java.util.UUID

object ModelEndpoints {
  val base = endpoint.in("models")

  val idLookup = base.get
    .in(path[UUID])
    .out(jsonBody[Model])
    .errorOut(oneOf(statusMapping(404, jsonBody[NotFound].description("not found"))))

  val create = base.post.in(jsonBody[Model]).out(jsonBody[Model])

  val list = base.get.out(jsonBody[List[Model]])

  val delete = base.delete
    .in(path[UUID])
    .out(jsonBody[DeleteMessage])
    .errorOut(oneOf(statusMapping(404, jsonBody[NotFound])))

  val endpoints = List(idLookup, create, list, delete)
}
