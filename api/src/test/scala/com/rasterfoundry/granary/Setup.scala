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

  def createExecution(
      execution: Execution.Create,
      service: ExecutionService[IO]
  ): OptionT[IO, Execution] = {
    val request =
      Request[IO](method = Method.POST, uri = Uri.uri("/executions")).withEntity(execution)
    for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[Execution] }
    } yield decoded

  }
}
