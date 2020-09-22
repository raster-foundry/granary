package com.rasterfoundry.granary.api

import cats.syntax.traverse._
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

package object endpoints {
  @newtype case class TokenHeader(headerValue: String)

  implicit val tokenHeaderCodec: Codec[String, TokenHeader, TextPlain] =
    Codec.string.mapDecode(s => DecodeResult.Value(TokenHeader(s)))(_.headerValue)

  implicit val listNonEmptyStringCodec: Codec[String, List[NonEmptyString], TextPlain] =
    Codec.string.mapDecode({
      case "" => DecodeResult.Value(Nil)
      case commaSepString =>
        DecodeResult.fromOption(
          commaSepString
            .split(",")
            .toList
            .traverse({ s =>
              NonEmptyString.from(s).toOption
            })
        )
    })(_.toList.mkString(","))
}
