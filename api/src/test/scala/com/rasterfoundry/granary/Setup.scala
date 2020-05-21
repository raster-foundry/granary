package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.datamodel._

import cats.data.OptionT
import cats.effect.IO
import org.http4s._

import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._

trait Setup {

  lazy val dataBucket = "raster-foundry-development-data-us-east-1"

  def createTask(task: Task.Create, service: TaskService[IO]): OptionT[IO, Task] = {
    val request =
      Request[IO](method = Method.POST, uri = Uri.uri("/tasks")).withEntity(task)
    for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[Task] }
    } yield decoded
  }

  def createPrediction(
      prediction: Prediction.Create,
      service: PredictionService[IO]
  ): OptionT[IO, Prediction] = {
    val request =
      Request[IO](method = Method.POST, uri = Uri.uri("/predictions")).withEntity(prediction)
    for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[Prediction] }
    } yield decoded

  }
}
