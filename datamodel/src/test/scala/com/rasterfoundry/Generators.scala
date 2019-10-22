package com.rasterfoundry.granary.datamodel

import cats.implicits._
import io.circe._
import io.circe.syntax._
import io.circe.testing._
import org.scalacheck._
import org.scalacheck.cats.implicits._
import org.scalacheck.Arbitrary._

import java.util.UUID

trait Generators extends ArbitraryInstances {

  private val shortStringGen: Gen[String] = Gen.listOfN(20, Gen.alphaChar) map { _.mkString }

  val modelGen: Gen[Model] =
    (Gen.delay(UUID.randomUUID), shortStringGen, arbitrary[JsonObject] map { jsObj =>
      new Validator(jsObj.asJson)
    }, shortStringGen, shortStringGen).tupled map {
      Function.tupled(Model.apply)
    }

  implicit val arbModel: Arbitrary[Model] = Arbitrary { modelGen }
}
