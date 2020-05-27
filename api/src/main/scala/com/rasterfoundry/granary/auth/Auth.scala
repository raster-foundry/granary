package com.rasterfoundry.granary.api.auth

import com.rasterfoundry.granary.api.AuthConfig
import com.rasterfoundry.granary.api.endpoints.TokenHeader
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.database.TokenDao
import com.rasterfoundry.granary.datamodel._

import cats.implicits._
import cats.effect.Sync
import doobie._
import doobie.implicits._
import eu.timepit.refined._
import eu.timepit.refined.auto._

class Auth[F[_]: Sync](authConfig: AuthConfig, xa: Transactor[F]) {

  private val anonymousToken = Token(
    TokenId(authConfig.anonymousTokenId),
    Email(refineMV("anonymous@granary.rasterfoundry.com")),
    UserId(authConfig.anonymousUserId)
  )

  def cleanToken(token: String): String = {
    token.replace("Bearer ", "").trim()
  }

  def validateToken[E](tokenHeaderO: Option[TokenHeader], fallback: => E): F[Either[E, Token]] =
    if (!authConfig.enabled) {
      Either.right[E, Token](anonymousToken).pure[F]
    } else {
      tokenHeaderO map { _.headerValue } traverse { token =>
        TokenDao.validateToken(token).transact(xa)
      } map { _.flatten } map { tokenO =>
        Either.fromOption(tokenO, fallback)
      }
    }

  def fallbackToForbidden(tokenHeaderO: Option[TokenHeader]): F[Either[CrudError, Token]] =
    validateToken(tokenHeaderO, Forbidden())
}
