package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.datamodel.PageRequest

import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import sttp.tapir._
import sttp.tapir.codec.refined._

object Inputs {

  val paginationInput: EndpointInput[PageRequest] =
    query[Option[NonNegInt]]("page").and(query[Option[PosInt]]("limit")).mapTo(PageRequest.apply _)
}
