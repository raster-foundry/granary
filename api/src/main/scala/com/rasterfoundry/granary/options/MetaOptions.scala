package com.rasterfoundry.granary.api.options

import com.rasterfoundry.granary.api.MetaConfig

import com.monovore.decline._
import com.monovore.decline.refined._
import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString

trait MetaOptions {
  private val apiHostHelp = "Host to provide in urls passed to AWS Batch jobs for status updates"

  private val apiHost: Opts[NonEmptyString] =
    Opts.option[NonEmptyString]("api-host", help = apiHostHelp) orElse Opts.env[NonEmptyString](
      "GRANARY_API_HOST",
      help = apiHostHelp
    ) withDefault (refineMV("http://localhost:9090"))

  def metaConfig: Opts[MetaConfig] =
    apiHost map { host => MetaConfig(host) }
}
