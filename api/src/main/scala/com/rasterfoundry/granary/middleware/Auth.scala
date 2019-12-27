package com.rasterfoundry.granary.api.middleware

import doobie._
import doobie.implicits._
import com.rasterfoundry.granary.database.TokenDao
import com.rasterfoundry.granary.api.error._
import cats.implicits._
import cats.data.Kleisli
import org.http4s.HttpRoutes
import org.http4s.Request
import cats.data.OptionT
import org.http4s.Response
import org.http4s.util.CaseInsensitiveString
import org.http4s.Status
import com.rasterfoundry.granary.api.AuthConfig
import org.http4s.Header
import cats.effect.Sync

object Auth {

  def cleanToken(token: String): String = {
    token.replace("Bearer ", "").trim()
  }

  def authorized(
      token: String
  ): ConnectionIO[Either[CrudError, Unit]] = {
    TokenDao
      .validateToken(token)
      .flatMap {
        case true =>
          Either.right[CrudError, Unit](()).pure[ConnectionIO]
        case _ => Either.left[CrudError, Unit](Forbidden()).pure[ConnectionIO]
      }
  }

  def customAuthMiddleware[F[_]: Sync](
      service: HttpRoutes[F],
      unauthedService: HttpRoutes[F],
      authConfig: AuthConfig,
      xa: Transactor[F]
  ): HttpRoutes[F] =
    Kleisli { req: Request[F] =>
      {
        (authConfig.enabled, req.headers.get(CaseInsensitiveString("Authorization"))) match {
          case (false, _) => (service <+> unauthedService)(req)
          case (_, Some(header: Header)) => {
            val token = cleanToken(header.value)
            for {
              authed <- OptionT.liftF(authorized(token).transact(xa))
              resp <- authed match {
                case Right(_) => (service <+> unauthedService)(req)
                case Left(_) =>
                  unauthedService(req).map(
                    res =>
                      res.status match {
                        case Status.NotFound => Response[F](Status.Forbidden)
                        case _               => res
                    }
                  )
              }
            } yield resp
          }
          case _ => {
            // auth enabled, no header provided
            unauthedService(req).map(
              res =>
                res.status match {
                  case Status.NotFound => Response[F](Status.Forbidden)
                  case _               => res
              }
            )
          }
        }
      }
    }
}
