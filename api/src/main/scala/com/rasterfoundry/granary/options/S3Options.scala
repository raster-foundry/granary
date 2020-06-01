package com.rasterfoundry.granary.api.options

import com.rasterfoundry.granary.api.S3Config

import com.monovore.decline._
import com.monovore.decline.refined._
import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString

trait S3Options {
  private val dataBucketHelp = "Where to store task grids, if submitted with executions"
  private val dataBucket: Opts[NonEmptyString] = Opts.option[NonEmptyString]("data-bucket", help = dataBucketHelp) orElse Opts.env[NonEmptyString]("GRANARY_DATA_BUCKET", help = dataBucketHelp) withDefault (refineMV("rasterfoundry-development-data-us-east-1"))

  def s3Config: Opts[S3Config] = dataBucket map { bucket => S3Config(bucket) }
}
