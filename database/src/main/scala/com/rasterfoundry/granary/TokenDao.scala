package com.rasterfoundry.granary.database

import com.rasterfoundry.granary.datamodel._

import cats.effect.{IO, LiftIO}
import cats.syntax.applicativeError._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.refined.implicits._
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import java.util.UUID

object TokenDao {

  implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

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
    } yield validated) handleErrorWith { e =>
      LiftIO[ConnectionIO].liftIO {
        Logger[IO].error(e)("Error validating token") map { _ =>
          Option.empty[Token]
        }
      }
    }
  }
}
