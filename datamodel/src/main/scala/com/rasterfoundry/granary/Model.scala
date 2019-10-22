package com.rasterfoundry.granary.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

case class Model(
    id: UUID,
    name: String,
    validator: Validator,
    jobDefinition: String,
    computeEnvironment: String
) {
  def validate = validator.validate _
}

object Model {
  implicit val encModel: Encoder[Model] = deriveEncoder

  implicit val decModel: Decoder[Model] = Decoder.forProduct5(
    "id",
    "name",
    "validator",
    "jobDefinition",
    "computeEnvironment"
  )(
    (id: UUID, name: String, validator: Json, jobDefinition: String, computeEnvironment: String) =>
      Model(id, name, new Validator(validator), jobDefinition, computeEnvironment)
  )
}
