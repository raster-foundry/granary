package com.rasterfoundry.granary.datamodel

import cats.kernel.Semigroup

case class PageRequest(
    page: Option[Int],
    limit: Option[Int]
)

object PageRequest {

  def default(defaultLimit: Int): PageRequest = PageRequest(Some(0), Some(defaultLimit))

  /** Combination of page requests chooses the left-most non-None value */
  implicit val monoidPageRequest: Semigroup[PageRequest] = new Semigroup[PageRequest] {

    def combine(x: PageRequest, y: PageRequest): PageRequest =
      PageRequest(x.page orElse y.page, x.limit orElse y.limit)
  }
}
