package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.datamodel.PageRequest

import sttp.tapir._

object Inputs {

  val paginationInput: EndpointInput[PageRequest] =
    query[Option[Int]]("page").and(query[Option[Int]]("limit")).mapTo(PageRequest.apply _)
}
