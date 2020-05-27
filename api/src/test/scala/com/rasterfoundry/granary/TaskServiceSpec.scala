package com.rasterfoundry.granary.api.services

import java.util.UUID

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.colisweb.tracing.NoOpTracingContext
import com.rasterfoundry.granary.api.AuthConfig
import com.rasterfoundry.granary.api.auth.Auth
import com.rasterfoundry.granary.api.endpoints.DeleteMessage
import com.rasterfoundry.granary.api.error.NotFound
import com.rasterfoundry.granary.database.TestDatabaseSpec
import com.rasterfoundry.granary.datamodel._
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.scalacheck._
import org.specs2.{ScalaCheck, Specification}

class TaskServiceSpec
    extends Specification
    with ScalaCheck
    with Generators
    with Setup
    with Teardown
    with TestDatabaseSpec {

  def is = sequential ^ s2"""
  This specification verifies that the Task Service can run without crashing

  The task service should:
    - create tasks                           $createExpectation
    - get tasks by id                        $getByIdExpectation
    - list tasks                             $listTasksExpectation
    - delete tasks                           $deleteTaskExpectation
"""

  val auth = new Auth(AuthConfig(false, UUID.randomUUID, UUID.randomUUID), transactor)

  val tracingContextBuilder = NoOpTracingContext.getNoOpTracingContextBuilder[IO].unsafeRunSync

  def service: TaskService[IO] =
    new TaskService[IO](
      PageRequest(Some(NonNegInt(0)), Some(PosInt(30))),
      tracingContextBuilder,
      transactor,
      auth
    )

  def createExpectation =
    prop { (task: Task.Create) =>
      {
        val out = for {
          created <- createTask(task, service)
          _       <- deleteTask(created, service)
        } yield created

        out.value.unsafeRunSync.get.toCreate ==== task
      }
    }

  def getByIdExpectation =
    prop { (task: Task.Create) =>
      {
        val getByIdAndBogus: OptionT[IO, (Task, Response[IO], NotFound)] = for {
          decoded <- createTask(task, service)
          successfulByIdRaw <- service.routes.run(
            Request[IO](
              method = Method.GET,
              uri = Uri.fromString(s"/tasks/${decoded.id}").right.get
            )
          )
          successfulById <- OptionT.liftF { successfulByIdRaw.as[Task] }
          missingByIdRaw <- service.routes.run(
            Request[IO](
              method = Method.GET,
              uri = Uri.fromString(s"/tasks/${UUID.randomUUID}").right.get
            )
          )
          missingById <- OptionT.liftF { missingByIdRaw.as[NotFound] }
          _           <- deleteTask(decoded, service)
        } yield { (successfulById, missingByIdRaw, missingById) }

        val (outTask, missingResp, missingBody) = getByIdAndBogus.value.unsafeRunSync.get

        outTask.toCreate ==== task && missingResp.status.code ==== 404 && missingBody ==== NotFound()

      }
    }

  def listTasksExpectation = {
    val tasks = Arbitrary.arbitrary[List[Task.Create]].sample.get.take(30)
    val listIO = for {
      tasks <- tasks traverse { task => createTask(task, service) }
      listedRaw <- service.routes.run(
        Request[IO](method = Method.GET, uri = Uri.uri("/tasks"))
      )
      listed <- OptionT.liftF { listedRaw.as[PaginatedResponse[Task]] }
      _      <- tasks traverse { task => deleteTask(task, service) }
    } yield (tasks, listed)

    val (inserted, listed) = listIO.value.unsafeRunSync.get
    listed.results.toSet.intersect(inserted.toSet) ==== inserted.toSet
  }

  def deleteTaskExpectation =
    prop { (task: Task.Create) =>
      {
        val deleteIO = for {
          decoded    <- createTask(task, service)
          deleteById <- deleteTask(decoded, service)
          missingByIdRaw <- service.routes.run(
            Request[IO](
              method = Method.DELETE,
              uri = Uri.fromString(s"/tasks/${UUID.randomUUID}").right.get
            )
          )
          missingById <- OptionT.liftF { missingByIdRaw.as[NotFound] }
        } yield { (deleteById, missingByIdRaw, missingById) }

        val (outDeleted, missingResp, missingBody) =
          deleteIO.value.unsafeRunSync.get

        outDeleted ==== DeleteMessage(
          1
        ) && missingResp.status.code ==== 404 && missingBody ==== NotFound()
      }
    }
}
