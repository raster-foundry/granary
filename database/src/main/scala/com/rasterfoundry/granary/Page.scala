package com.rasterfoundry.granary.database

import com.rasterfoundry.granary.datamodel.PageRequest

import doobie.Fragment
import doobie.implicits._

object Page {

  def apply(fragment: Fragment, pageRequest: PageRequest): Fragment = {
    (for {
      lim  <- pageRequest.limit
      page <- pageRequest.page
    } yield {
      fragment ++ fr"LIMIT $lim OFFSET ${page * lim}"
    }) getOrElse { fragment }

  }
}
