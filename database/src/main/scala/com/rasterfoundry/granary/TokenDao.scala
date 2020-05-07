package com.rasterfoundry.granary.database

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID
import cats.effect.LiftIO
import cats.effect.IO

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
  ): ConnectionIO[Boolean] =
    (for {
      uuid <- LiftIO[ConnectionIO].liftIO(IO { UUID.fromString(id) })
      exists <- (
          fr"SELECT EXISTS(" ++
            selectF ++
            Fragments.whereAnd(fr"id = $uuid") ++ fr")"
      ).query[Boolean].unique
    } yield exists).attempt.map {
      case Right(true) => true
      case _           => false
    }
}
