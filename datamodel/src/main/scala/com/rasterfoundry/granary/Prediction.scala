package com.rasterfoundry.granary.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.time.Instant
import java.util.UUID

case class Prediction(
    id: UUID,
    modelId: UUID,
    invokedAt: Instant,
    arguments: Json,
    status: JobStatus,
    statusReason: Option[String],
    outputLocation: Option[String]
)

object Prediction {
  implicit val encPrediction: Encoder[Prediction] = deriveEncoder
  implicit val decPrediction: Decoder[Prediction] = deriveDecoder

  case class Create(
      modelId: UUID,
      arguments: Json
  )

  object Create {
    implicit val encCreate: Encoder[Create] = deriveEncoder
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }
}
