package com.rasterfoundry.granary.database

import com.rasterfoundry.granary.datamodel._

import cats.effect.{IO, LiftIO}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.refined.implicits._

import java.util.UUID

object TokenDao {
  sealed abstract class TokenDaoError extends Throwable
  case object InvalidToken            extends TokenDaoError

  val selectF =
    fr"""
  SELECT id, email, user_id FROM tokens
  """

  def validateToken(
      tokenId: String
  ): ConnectionIO[Option[Token]] = {
    (for {
      uuid <- LiftIO[ConnectionIO].liftIO(IO { UUID.fromString(tokenId) })
      validated <- (
          selectF ++ Fragments.whereAnd(fr"id = $uuid")
      ).query[Token].option
    } yield validated) handleErrorWith { _ => Option.empty[Token].pure[ConnectionIO] }
  }
}
