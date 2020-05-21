package com.rasterfoundry.granary.api.services

import com.rasterfoundry.granary.datamodel._

import cats.data.OptionT
import cats.effect.IO
import org.http4s._

import org.http4s.circe.CirceEntityDecoder._
import com.rasterfoundry.granary.api.endpoints.DeleteMessage

trait Teardown {

  def deleteTask(task: Task, service: TaskService[IO]): OptionT[IO, DeleteMessage] = {
    val request =
      Request[IO](method = Method.DELETE, uri = Uri.fromString(s"/tasks/${task.id}").right.get)
    for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[DeleteMessage] }
    } yield decoded
  }

}
