package com.rasterfoundry.granary.datamodel

import cats.kernel.Semigroup
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}

case class PageRequest(
    page: Option[NonNegInt],
    limit: Option[PosInt]
)

object PageRequest {

  def default(defaultLimit: PosInt): PageRequest =
    PageRequest(Some(NonNegInt(0)), Some(defaultLimit))

  /** Combination of page requests chooses the left-most non-None value */
  implicit val monoidPageRequest: Semigroup[PageRequest] = new Semigroup[PageRequest] {

    def combine(x: PageRequest, y: PageRequest): PageRequest =
      PageRequest(x.page orElse y.page, x.limit orElse y.limit)
  }
}
