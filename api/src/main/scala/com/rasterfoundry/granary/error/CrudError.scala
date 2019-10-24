package com.rasterfoundry.granary.api.error

import io.circe._
import io.circe.generic.semiauto._

case class NotFound(msg: String = "Not found")

object NotFound {
  implicit val encNotFound: Encoder[NotFound] = deriveEncoder
  implicit val decNotFound: Decoder[NotFound] = deriveDecoder
}

case class ValidationError(msg: String)

object ValidationError {
  implicit val encValidationError: Encoder[ValidationError] = deriveEncoder
  implicit val decValidationError: Decoder[ValidationError] = deriveDecoder
}
