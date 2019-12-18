package com.rasterfoundry.granary.api

import doobie._
import doobie.implicits._
import com.rasterfoundry.granary.database.TokenDao
import com.rasterfoundry.granary.api.error._
import cats.implicits._

object Auth {

  def cleanToken(token: String): String = {
    token.replace("Bearer ", "").trim()
  }

  def authorized[A](
      tokenO: Option[String],
      authEnabled: Boolean,
      io: ConnectionIO[A]
  ): ConnectionIO[Either[CrudError, A]] = {
    (tokenO.map(cleanToken(_)), authEnabled) match {
      case (Some(token), true) =>
        println(s"Auth with token: $token")
        TokenDao
          .validateToken(token, authEnabled)
          .flatMap {
            case Right(true) =>
              io.map((r: A) => Either.right[CrudError, A](r))
            case _ => Either.left[CrudError, A](Forbidden()).pure[ConnectionIO]
          }
      case (_, true) =>
        Either.left[CrudError, A](Forbidden()).pure[ConnectionIO]
      case _ => io.map((r: A) => Either.right[CrudError, A](r))
    }
  }
}
