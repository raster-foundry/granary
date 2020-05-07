package com.rasterfoundry.granary.datamodel

import cats.implicits._
import com.azavea.stac4s.StacItemAsset
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

sealed abstract class PredictionStatusUpdate

object PredictionStatusUpdate {

  implicit val decStatusUpdate: Decoder[PredictionStatusUpdate] =
    Decoder[PredictionFailure].widen or Decoder[
      PredictionSuccess
    ].widen

  implicit val encStatusUpdate: Encoder[PredictionStatusUpdate] =
    new Encoder[PredictionStatusUpdate] {

      def apply(t: PredictionStatusUpdate): Json =
        t match {
          case ps: PredictionFailure => ps.asJson
          case ps: PredictionSuccess => ps.asJson
        }
    }
}

case class PredictionFailure(
    message: String
) extends PredictionStatusUpdate

object PredictionFailure {
  implicit val encPredictionFailure: Encoder[PredictionFailure] = deriveEncoder
  implicit val decPredictionFailure: Decoder[PredictionFailure] = deriveDecoder
}

case class PredictionSuccess(
    results: List[StacItemAsset]
) extends PredictionStatusUpdate

object PredictionSuccess {
  implicit val encPredictionSuccess: Encoder[PredictionSuccess] = deriveEncoder
  implicit val decPredictionSuccess: Decoder[PredictionSuccess] = deriveDecoder
}
