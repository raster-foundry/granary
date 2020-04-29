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
      pageRequest.page map { _.value } getOrElse 0,   // ultimate fallback -- default value apparently wasn't applied
      pageRequest.limit map { _.value } getOrElse 30, // ultimate fallback -- default value apparently wasn't applied
      results
    )
}
