package com.rasterfoundry.granary

import com.rasterfoundry.granary.database.meta.{CirceJsonbMeta, EnumMeta}
import com.rasterfoundry.granary.datamodel._

import io.estatico.newtype.Coercible
import doobie._

package object database extends CirceJsonbMeta with EnumMeta {

  implicit def newTypePut[N: Coercible[R, *], R: Put]: Put[N] =
    Put[R].contramap[N](_.asInstanceOf[R])

  implicit def newTypeRead[N: Coercible[R, *], R: Read]: Read[N] = Read[R].map(_.asInstanceOf[N])

  def tokenToUserId(token: Token): Option[UserId] =
    if (token.userEmail.emailString.value == "anonymous@granary.rasterfoundry.com") {
      None
    } else {
      Some(token.userId)
    }
}
