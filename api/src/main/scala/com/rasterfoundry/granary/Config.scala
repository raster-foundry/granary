package com.rasterfoundry.granary.api

import eu.timepit.refined.types.numeric.PosInt

import java.util.UUID

case class TracingConfig(
    tracingSink: String
)

case class S3Config(dataBucket: String)

case class MetaConfig(apiHost: String)

case class AuthConfig(enabled: Boolean, anonymousUserId: UUID, anonymousTokenId: UUID)

case class PaginationConfig(defaultLimit: PosInt)
