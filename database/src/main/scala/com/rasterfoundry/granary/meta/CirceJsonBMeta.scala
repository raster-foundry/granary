package com.rasterfoundry.granary.database.meta

import com.rasterfoundry.granary.datamodel.Validator
import com.azavea.stac4s.StacItemAsset
import doobie._
import doobie.postgres.circe.jsonb.implicits._
import io.circe._
import io.circe.syntax._

import scala.reflect.runtime.universe.TypeTag

object CirceJsonbMeta {

  def apply[Type: TypeTag: Encoder: Decoder] = {
    val get = Get[Json].tmap[Type](_.as[Type].valueOr(throw _))
    val put = Put[Json].tcontramap[Type](_.asJson)
    new Meta[Type](get, put)
  }
}

trait CirceJsonbMeta {
  implicit val validatorMeta: Meta[Validator] = CirceJsonbMeta[Validator]

  implicit val stacItemAssetListMeta: Meta[List[StacItemAsset]] =
    CirceJsonbMeta[List[StacItemAsset]]
}
