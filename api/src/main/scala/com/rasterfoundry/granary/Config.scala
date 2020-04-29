package com.rasterfoundry.granary.api

import eu.timepit.refined.types.numeric.PosInt

case class TracingConfig(
    tracingSink: String
)

case class S3Config(dataBucket: String)

case class MetaConfig(apiHost: String)

case class AuthConfig(enabled: Boolean)

case class PaginationConfig(defaultLimit: PosInt)
