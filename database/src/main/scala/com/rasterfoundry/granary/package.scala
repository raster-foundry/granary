package com.rasterfoundry.granary

import com.rasterfoundry.granary.database.meta.{CirceJsonbMeta, EnumMeta}
import com.rasterfoundry.granary.datamodel._

import cats.syntax.traverse._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.Coercible

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

  def tokenToFilter(token: Token): Option[Fragment] =
    tokenToUserId(token) map { userId =>
      fr"owner = $userId"
    }

  implicit val getListNonEmptyString: Get[List[NonEmptyString]] =
    Get[List[String]].temap({ strings =>
      strings traverse { s =>
        NonEmptyString.from(s)
      }
    })

  implicit val putListNonEmptyString: Put[List[NonEmptyString]] =
    Put[List[String]].contramap(_.toList.map(_.value))
}
