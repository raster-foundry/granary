package com.rasterfoundry.granary.api

import io.circe._
import io.circe.generic.semiauto._

case class HelloError(msg: String)

object HelloError {
  implicit val encHelloError: Encoder[HelloError] = deriveEncoder
  implicit val decHelloError: Decoder[HelloError] = deriveDecoder
}
