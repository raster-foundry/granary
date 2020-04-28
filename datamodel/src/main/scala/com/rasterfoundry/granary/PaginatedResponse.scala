package com.rasterfoundry.granary.datamodel

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class PaginatedResponse[T: Encoder: Decoder](
    page: Int,
    pageSize: Int,
    results: List[T]
)

object PaginatedResponse {

  implicit def encPaginatedResponse[T: Encoder: Decoder]: Encoder[PaginatedResponse[T]] =
    deriveEncoder

  implicit def decPaginatedResponse[T: Encoder: Decoder]: Decoder[PaginatedResponse[T]] =
    deriveDecoder

  def forRequest[T: Encoder: Decoder](
      results: List[T],
      pageRequest: PageRequest
  ): PaginatedResponse[T] =
    PaginatedResponse(
      pageRequest.page getOrElse -1,
      pageRequest.limit getOrElse -1,
      results
    )
}
