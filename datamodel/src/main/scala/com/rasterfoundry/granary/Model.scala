package com.rasterfoundry.granary.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

case class Model(
    id: UUID,
    name: String,
    validator: Validator,
    jobDefinition: String,
    jobQueue: String
) {
  def validate = validator.validate _

  def toCreate: Model.Create =
    Model.Create(
      name,
      validator,
      jobDefinition,
      jobQueue
    )
}

object Model {
  implicit val encModel: Encoder[Model] = deriveEncoder

  implicit val decModel: Decoder[Model] = Decoder.forProduct5(
    "id",
    "name",
    "validator",
    "jobDefinition",
    "jobQueue"
  )(
    Model.apply _
  )

  case class Create(
      name: String,
      validator: Validator,
      jobDefinition: String,
      jobQueue: String
  )

  object Create {
    implicit val encCreate: Encoder[Create] = deriveEncoder
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }
}
