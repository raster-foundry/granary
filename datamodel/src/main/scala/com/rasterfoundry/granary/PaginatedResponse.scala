package com.rasterfoundry.granary.datamodel

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.refined._
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}

case class PaginatedResponse[T: Encoder: Decoder](
    page: NonNegInt,
    pageSize: PosInt,
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
      pageRequest.page getOrElse NonNegInt(
        0
      ), // ultimate fallback -- default value apparently wasn't applied
      pageRequest.limit getOrElse PosInt(
        30
      ), // ultimate fallback -- default value apparently wasn't applied
      results
    )
}
