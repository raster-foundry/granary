package com.rasterfoundry.granary.datamodel

import cats.implicits._
import io.circe.syntax._
import org.scalacheck._
import org.scalacheck.cats.implicits._

trait Generators {

  private val shortStringGen: Gen[String] = Gen.listOfN(20, Gen.alphaChar) map { _.mkString }

  val modelGen: Gen[Model.Create] =
    (shortStringGen,
     Gen.delay(new Validator(Map.empty[String, String].asJson)),
     shortStringGen,
     shortStringGen).tupled map {
      Function.tupled(Model.Create.apply)
    }

  implicit val arbModel: Arbitrary[Model.Create] = Arbitrary { modelGen }
}
