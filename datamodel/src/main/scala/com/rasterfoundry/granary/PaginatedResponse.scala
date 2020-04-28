package com.rasterfoundry.granary.datamodel

import io.circe.Encoder
import io.circe.generic.semiauto._

case class PaginatedResponse[T: Encoder](
    page: Int,
    count: Int,
    results: List[T]
)

object PaginatedResponse {
  implicit def encPaginatedResponse[T: Encoder]: Encoder[PaginatedResponse[T]] = deriveEncoder
}
