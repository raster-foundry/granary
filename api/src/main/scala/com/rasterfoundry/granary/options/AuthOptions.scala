package com.rasterfoundry.granary.api.options

import com.rasterfoundry.granary.api.AuthConfig
import com.monovore.decline._

import java.util.UUID

trait AuthOptions {

  private val authEnabledHelp = "Whether to enable support for user authentication"
  private val authEnabled     = Opts.flag("with-auth", help = authEnabledHelp).orFalse

  private val anonymousUserIdHelp =
    "UUID corresponding to the 'anonymous' user when auth is not enabled"

  private val anonymousUserId: Opts[UUID] =
    Opts.option[UUID]("anonymous-user-id", help = anonymousUserIdHelp) orElse Opts.env[UUID](
      "ANONYMOUS_USER_ID",
      help = anonymousUserIdHelp
    ) withDefault (UUID.fromString("6a1988bd-2ac3-4836-860b-9607b60afd5c"))

  private val anonymousUserTokenHelp =
    "UUID corresponding to the 'anonymous' user's token"

  private val anonymousUserToken: Opts[UUID] =
    Opts.option[UUID]("anonymous-user-token", help = anonymousUserTokenHelp) orElse Opts.env[UUID](
      "ANONYMOUS_TOKEN_ID",
      help = anonymousUserTokenHelp
    ) withDefault (UUID.fromString("6a1988bd-2ac3-4836-860b-9607b60afd5c"))

  def authConfig: Opts[AuthConfig] =
    (
      authEnabled,
      anonymousUserId,
      anonymousUserToken
    ) mapN { AuthConfig.apply }

}
