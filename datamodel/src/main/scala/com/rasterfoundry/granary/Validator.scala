package com.rasterfoundry.granary.datamodel

import cats.data.ValidatedNel
import io.circe._
import io.circe.generic.semiauto._
import io.circe.schema.{Schema, ValidationError}

case class Validator(schema: Json) extends Schema {
  private val validator                                          = Schema.load(schema)
  def validate(value: Json): ValidatedNel[ValidationError, Unit] = validator.validate(value)
}

object Validator {
  implicit val encValidator: Encoder[Validator] = deriveEncoder
  implicit val decValidator: Decoder[Validator] = deriveDecoder
}
