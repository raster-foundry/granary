package com.rasterfoundry.granary.datamodel

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

sealed abstract class HealthcheckResult

case class HealthyResult() extends HealthcheckResult

object HealthyResult {

  implicit val encHealthyResult: Encoder[HealthyResult] = new Encoder[HealthyResult] {
    def apply(thing: HealthyResult): Json = ().asJson
  }

  implicit val decHealthyResult: Decoder[HealthyResult] = Decoder[JsonObject] map { _ =>
    HealthyResult()
  }
}

case class UnhealthyResult(database: HealthResult) extends HealthcheckResult

object UnhealthyResult {
  implicit val encUnhealthyResult: Encoder[UnhealthyResult] = deriveEncoder
  implicit val decUnhealthyResult: Decoder[UnhealthyResult] = deriveDecoder
}
