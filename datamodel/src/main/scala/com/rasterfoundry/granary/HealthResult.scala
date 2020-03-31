package com.rasterfoundry.granary.datamodel

import io.circe._

sealed abstract class HealthResult(val repr: String) {
  override def toString: String = repr
}

object HealthResult {
  case object Healthy   extends HealthResult("healthy")
  case object Unhealthy extends HealthResult("unhealthy")

  def fromStringE(s: String): Either[String, HealthResult] =
    s.toLowerCase match {
      case "healthy"   => Right(Healthy)
      case "unhealthy" => Right(Unhealthy)
      case s           => Left(s"$s is not a valid health result")
    }

  implicit val encHealthResult: Encoder[HealthResult] = Encoder.encodeString.contramap(_.toString)
  implicit val decHealthResult: Decoder[HealthResult] = Decoder.decodeString.emap(fromStringE)
}
