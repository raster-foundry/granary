package com.rasterfoundry.granary.api.endpoints

import com.rasterfoundry.granary.datamodel.ModelId

import tapir.{Codec, DecodeResult, MediaType}

trait NewTypeCodec {

  implicit def modelIdTextCodec: Codec[ModelId, MediaType.TextPlain, _] =
    Codec.uuidPlainCodec.mapDecode(
      value => DecodeResult.Value(ModelId(value))
    )(modelId => modelId.toUUID)
}
