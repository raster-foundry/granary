package com.rasterfoundry.granary.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class JobStatus(val repr: String) {
  override def toString = repr
}

object JobStatus {

  case object Created    extends JobStatus("CREATED")
  case object Started    extends JobStatus("STARTED")
  case object Successful extends JobStatus("SUCCESSFUL")
  case object Failed     extends JobStatus("FAILED")

  def fromString(s: String): JobStatus =
    s.toUpperCase match {
      case "CREATED"    => Created
      case "STARTED"    => Started
      case "SUCCESSFUL" => Successful
      case "FAILED"     => Failed
      case _            => throw new Exception(s"Invalid string: $s")
    }

  implicit val jobStatusEncoder: Encoder[JobStatus] =
    Encoder.encodeString.contramap[JobStatus](_.toString)

  implicit val jobStatusDecoder: Decoder[JobStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "JobStatus")
    }
}
