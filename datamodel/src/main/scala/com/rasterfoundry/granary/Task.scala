package com.rasterfoundry.granary.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

case class Task(
    id: UUID,
    name: String,
    validator: Validator,
    jobDefinition: String,
    jobQueue: String
) {
  def validate = validator.validate _

  def toCreate: Task.Create =
    Task.Create(
      name,
      validator,
      jobDefinition,
      jobQueue
    )
}

object Task {
  implicit val encTask: Encoder[Task] = deriveEncoder

  implicit val decTask: Decoder[Task] = Decoder.forProduct5(
    "id",
    "name",
    "validator",
    "jobDefinition",
    "jobQueue"
  )(
    Task.apply _
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
