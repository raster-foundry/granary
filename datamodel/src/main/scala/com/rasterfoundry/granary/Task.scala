package com.rasterfoundry.granary.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

case class Task(
    id: UUID,
    name: String,
    validator: Validator,
    jobDefinition: String,
    jobQueue: String,
    owner: Option[UUID]
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

  implicit val decTask: Decoder[Task] = Decoder.forProduct6(
    "id",
    "name",
    "validator",
    "jobDefinition",
    "jobQueue",
    "owner"
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
