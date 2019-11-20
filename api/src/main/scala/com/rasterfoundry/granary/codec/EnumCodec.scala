package com.rasterfoundry.granary.api.codec

import com.rasterfoundry.granary.datamodel._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir._

import scala.util.{Failure, Success, Try}

trait EnumCodec {
  private def decJobStatus(s: String): DecodeResult[JobStatus] =
    Try { JobStatus.fromString(s) } match {
      case Success(status) => DecodeResult.Value(status)
      case Failure(err)    => DecodeResult.Error(err.getMessage(), err)
    }

  implicit val jobStatusCodec: Codec[JobStatus, TextPlain, String] =
    Codec.stringPlainCodecUtf8.mapDecode(decJobStatus)(_.toString)
}
