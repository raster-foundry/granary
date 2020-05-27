package com.rasterfoundry.granary

import io.estatico.newtype.macros.newtype
import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.collection.Contains

import java.util.UUID

package object datamodel {
  @newtype case class UserId(toUUID: UUID)
  @newtype case class TokenId(toUUID: UUID)
  @newtype case class Email(emailString: String Refined Contains[W.`'@'`.T])
}
