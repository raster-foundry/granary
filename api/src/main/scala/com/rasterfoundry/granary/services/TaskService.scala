package com.rasterfoundry.granary.api.services

import cats._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel.{PageRequest, PaginatedResponse, Task}
import com.rasterfoundry.granary.database.TaskDao
import doobie._
import doobie.implicits._
import org.http4s._
import sttp.tapir.server.http4s._

import java.util.UUID

class TaskService[F[_]: Sync](
    defaultPageRequest: PageRequest,
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F]
)(implicit
    contextShift: ContextShift[F]
) extends GranaryService {

  def createTask(task: Task.Create): F[Either[Unit, Task]] =
    mkContext("createTask", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        TaskDao.insertTask(task).transact(xa)
      )(Right(_))
    }

  def listTasks(pageRequest: PageRequest): F[Either[Unit, PaginatedResponse[Task]]] = {
    val forPage = pageRequest `combine` defaultPageRequest
    mkContext("listTasks", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        TaskDao.listTasks(forPage).transact(xa)
      )(tasks => Right(PaginatedResponse.forRequest(tasks, forPage)))
    }
  }

  def getById(id: UUID): F[Either[CrudError, Task]] =
    mkContext("lookupTaskById", Map("taskId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(
        TaskDao.getTask(id).transact(xa)
      )({
        case Some(task) => Right(task)
        case None       => Left(NotFound())
      })
    }

  def deleteById(id: UUID): F[Either[CrudError, DeleteMessage]] =
    mkContext("deleteTaskById", Map("taskId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(TaskDao.deleteTask(id).transact(xa))({
        case Some(n) => Right(DeleteMessage(n))
        case None    => Left(NotFound())
      })
    }

  val create = TaskEndpoints.create.toRoutes(createTask)
  val list   = TaskEndpoints.list.toRoutes(listTasks)
  val detail = TaskEndpoints.idLookup.toRoutes(getById)
  val delete = TaskEndpoints.delete.toRoutes(deleteById)

  val routes: HttpRoutes[F] = delete <+> detail <+> create <+> list
}
