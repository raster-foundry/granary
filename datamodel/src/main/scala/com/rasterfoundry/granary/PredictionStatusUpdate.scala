package com.rasterfoundry.granary.datamodel

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._

sealed abstract class PredictionStatusUpdate

object PredictionStatusUpdate {

  implicit val decStatusUpdate
      : Decoder[PredictionStatusUpdate] = Decoder[PredictionFailure].widen or Decoder[
    PredictionSuccess
  ].widen

  implicit val encStatusUpdate: Encoder[PredictionStatus] = new Encoder[PredictionStatus] {

    def apply(t: Thing): Json = t match {
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
    outputLocation: String
) extends PredictionStatusUpdate

object PredictionSuccess {
  implicit val encPredictionSuccess: Encoder[PredictionSuccess] = deriveEncoder
  implicit val decPredictionSuccess: Decoder[PredictionSuccess] = deriveDecoder
}
