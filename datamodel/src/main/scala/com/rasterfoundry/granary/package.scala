package com.rasterfoundry.granary

import io.circe._
import io.estatico.newtype.macros.newtype

import scala.language.implicitConversions

import java.util.UUID

package object datamodel {

  @newtype case class ModelId(toUUID: UUID)

  object ModelId {

    implicit def encModelId: Encoder[ModelId] = new Encoder[ModelId] {
      def apply(thing: ModelId): Json = Encoder[UUID].apply(thing.toUUID)
    }
    implicit def decModelId: Decoder[ModelId] = Decoder[UUID] map { ModelId(_) }
  }
}
