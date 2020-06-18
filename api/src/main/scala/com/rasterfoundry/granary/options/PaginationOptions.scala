package com.rasterfoundry.granary.api.options

import com.rasterfoundry.granary.api.PaginationConfig

import com.monovore.decline._
import com.monovore.decline.refined._
import eu.timepit.refined._
import eu.timepit.refined.types.numeric.PosInt

trait PaginationOptions {
  private val defaultPageSizeHelp = "Default page size for list endpoints"

  private val defaultPageSize: Opts[PosInt] =
    Opts.option[PosInt]("default-page-size", help = defaultPageSizeHelp) orElse Opts
      .env[PosInt]("DEFAULT_PAGE_SIZE", help = defaultPageSizeHelp) withDefault (refineMV(30))

  def paginationConfig: Opts[PaginationConfig] =
    defaultPageSize map { pageSize => PaginationConfig(pageSize) }
}
