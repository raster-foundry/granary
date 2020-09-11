package com.rasterfoundry.granary.datamodel

import cats.syntax.functor._
import com.azavea.stac4s.StacItemAsset
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

sealed abstract class ExecutionStatusUpdate

object ExecutionStatusUpdate {

  implicit val decStatusUpdate: Decoder[ExecutionStatusUpdate] =
    Decoder[ExecutionFailure].widen or Decoder[
      ExecutionSuccess
    ].widen

  implicit val encStatusUpdate: Encoder[ExecutionStatusUpdate] =
    new Encoder[ExecutionStatusUpdate] {

      def apply(t: ExecutionStatusUpdate): Json =
        t match {
          case ps: ExecutionFailure => ps.asJson
          case ps: ExecutionSuccess => ps.asJson
        }
    }
}

case class ExecutionFailure(
    message: String
) extends ExecutionStatusUpdate

object ExecutionFailure {
  implicit val encExecutionFailure: Encoder[ExecutionFailure] = deriveEncoder
  implicit val decExecutionFailure: Decoder[ExecutionFailure] = deriveDecoder
}

case class ExecutionSuccess(
    results: List[StacItemAsset]
) extends ExecutionStatusUpdate

object ExecutionSuccess {
  implicit val encExecutionSuccess: Encoder[ExecutionSuccess] = deriveEncoder
  implicit val decExecutionSuccess: Decoder[ExecutionSuccess] = deriveDecoder
}
