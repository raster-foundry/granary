package com.rasterfoundry.granary.database

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object TokenDao {
  sealed abstract class TokenDaoError extends Throwable
  case object InvalidToken            extends TokenDaoError

  val selectF =
    fr"""
  SELECT id
  FROM tokens
  """

  def validateToken(
      id: String
  ): ConnectionIO[Either[TokenDaoError, Boolean]] =
    try {
      val uuid = UUID.fromString(id)
      (
        fr"SELECT EXISTS(" ++
          selectF ++
          Fragments.whereAnd(fr"id = $uuid") ++ fr")"
      ).query[Boolean].unique.map { exists =>
        exists match {
          case true  => Right(true)
          case false => Left(InvalidToken)
        }
      }
    } catch {
      case _: IllegalArgumentException =>
        Either.left[TokenDaoError, Boolean](InvalidToken).pure[ConnectionIO]
    }
}
