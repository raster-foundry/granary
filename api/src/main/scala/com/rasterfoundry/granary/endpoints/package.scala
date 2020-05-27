package com.rasterfoundry.granary.api

import io.estatico.newtype.macros.newtype
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

package object endpoints {
  @newtype case class TokenHeader(headerValue: String)

  implicit val tokenHeaderCodec: Codec[String, TokenHeader, TextPlain] =
    Codec.string.mapDecode(s => DecodeResult.Value(TokenHeader(s)))(_.headerValue)
}
