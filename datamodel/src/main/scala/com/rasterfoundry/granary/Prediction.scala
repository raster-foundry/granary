package com.rasterfoundry.granary.datamodel

import com.amazonaws.services.s3.{AmazonS3, AmazonS3URI}
import com.azavea.stac4s.StacItemAsset
import io.circe._
import io.circe.generic.semiauto._

import scala.util.Try

import java.time.Instant
import java.util.{Date, UUID}

case class Prediction(
    id: UUID,
    taskId: UUID,
    invokedAt: Instant,
    arguments: Json,
    status: JobStatus,
    statusReason: Option[String],
    results: List[StacItemAsset],
    webhookId: Option[UUID]
) {

  def signS3OutputLocation(s3Client: AmazonS3): Prediction = {
    val updatedResults = results map {
      case asset if asset.href.startsWith("s3://") =>
        (Try {
          val s3Url      = new AmazonS3URI(asset.href)
          val expiryDate = Date.from(Instant.now().plusSeconds(60 * 60))
          asset.copy(href =
            s3Client.generatePresignedUrl(s3Url.getBucket, s3Url.getKey, expiryDate).toString
          )
        }).fold(_ => asset, identity)
      case location => location
    }
    this.copy(results = updatedResults)
  }
}

object Prediction {
  implicit val encPrediction: Encoder[Prediction] = deriveEncoder
  implicit val decPrediction: Decoder[Prediction] = deriveDecoder

  case class Create(
      taskId: UUID,
      arguments: Json
  )

  object Create {
    implicit val encCreate: Encoder[Create] = deriveEncoder
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }
}
