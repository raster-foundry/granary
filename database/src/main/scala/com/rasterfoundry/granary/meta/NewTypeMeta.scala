package com.rasterfoundry.granary.database.meta

import com.rasterfoundry.granary.datamodel.ModelId

import doobie._
import doobie.postgres.implicits._

import java.util.UUID

trait NewTypeMeta {
  implicit val putModelId: Put[ModelId] = Put[UUID].contramap[ModelId](_.toUUID)
  implicit val getModelId: Get[ModelId] = Get[UUID].map(ModelId.apply(_))
}
