package com.rasterfoundry.granary.datamodel

import cats.implicits._
import io.circe.syntax._
import org.scalacheck._
import org.scalacheck.cats.implicits._

import java.util.UUID

trait Generators {

  private val shortStringGen: Gen[String] = Gen.listOfN(20, Gen.alphaChar) map { _.mkString }

  val modelGen: Gen[Model.Create] =
    (
      shortStringGen,
      Gen.delay(new Validator(Map.empty[String, String].asJson)),
      shortStringGen,
      shortStringGen
    ).tupled map {
      Function.tupled(Model.Create.apply)
    }

  val predictionGen: Gen[Prediction.Create] =
    (Gen.delay(UUID.randomUUID), Gen.const(Map.empty[String, String].asJson)).tupled map {
      Function.tupled(Prediction.Create.apply)
    }

  implicit val arbModel: Arbitrary[Model.Create] = Arbitrary { modelGen }

  implicit val arbPrediction: Arbitrary[Prediction.Create] = Arbitrary { predictionGen }
}
