package com.rasterfoundry.granary.api

case class TracingConfig(
    tracingSink: String
)

case class S3Config(dataBucket: String)

case class MetaConfig(apiHost: String)

case class AuthConfig(enabled: Boolean)

case class PaginationConfig(defaultLimit: Int)
