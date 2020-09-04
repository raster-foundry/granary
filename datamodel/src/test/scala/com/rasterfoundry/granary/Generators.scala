package com.rasterfoundry.granary.datamodel
import io.circe.syntax._
import org.scalacheck._
import org.scalacheck.cats.implicits._

import java.util.UUID

trait Generators {

  private val shortStringGen: Gen[String] = Gen.listOfN(20, Gen.alphaChar) map { _.mkString }

  val taskGen: Gen[Task.Create] =
    (
      shortStringGen,
      Gen.delay(new Validator(Map.empty[String, String].asJson)),
      shortStringGen,
      shortStringGen
    ).tupled map {
      Function.tupled(Task.Create.apply)
    }

  val executionGen: Gen[Execution.Create] =
    (
      shortStringGen,
      Gen.delay(UUID.randomUUID),
      Gen.const(Map.empty[String, String].asJson)
    ).tupled map {
      Function.tupled(Execution.Create.apply)
    }

  implicit val arbTask: Arbitrary[Task.Create] = Arbitrary { taskGen }

  implicit val arbExecution: Arbitrary[Execution.Create] = Arbitrary { executionGen }
}
