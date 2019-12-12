package com.rasterfoundry.granary.datamodel

import io.circe._
import io.circe.generic.semiauto._
import java.time.Instant
import java.util.{Date, UUID}

import com.amazonaws.services.s3.{AmazonS3, AmazonS3URI}

import scala.util.{Failure, Success, Try}

case class Prediction(
    id: UUID,
    modelId: UUID,
    invokedAt: Instant,
    arguments: Json,
    status: JobStatus,
    statusReason: Option[String],
    outputLocation: Option[String],
    webhookId: Option[UUID]
) {

  def signS3OutputLocation(s3Client: AmazonS3): Prediction = {
    val updatedLocation = outputLocation map {
      case s3Location if s3Location.startsWith("s3://") =>
        Try {
          val s3Url      = new AmazonS3URI(s3Location)
          val expiryDate = Date.from(Instant.now().plusSeconds(60 * 60))
          s3Client.generatePresignedUrl(s3Url.getBucket, s3Url.getKey, expiryDate)
        } match {
          case Success(v) => v.toString
          case Failure(_) => s3Location
        }
      case location => location
    }
    this.copy(outputLocation = updatedLocation)
  }
}

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
