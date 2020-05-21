package com.rasterfoundry.granary.datamodel

import cats.implicits._
import io.circe.syntax._
import org.scalacheck._
import org.scalacheck.cats.implicits._

import java.util.UUID

trait Generators {

  private val shortStringGen: Gen[String] = Gen.listOfN(20, Gen.alphaChar) map { _.mkString }

  val modelGen: Gen[Task.Create] =
    (
      shortStringGen,
      Gen.delay(new Validator(Map.empty[String, String].asJson)),
      shortStringGen,
      shortStringGen
    ).tupled map {
      Function.tupled(Task.Create.apply)
    }

  val predictionGen: Gen[Execution.Create] =
    (Gen.delay(UUID.randomUUID), Gen.const(Map.empty[String, String].asJson)).tupled map {
      Function.tupled(Execution.Create.apply)
    }

  implicit val arbModel: Arbitrary[Task.Create] = Arbitrary { modelGen }

  implicit val arbPrediction: Arbitrary[Execution.Create] = Arbitrary { predictionGen }
}
