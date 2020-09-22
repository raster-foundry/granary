package com.rasterfoundry.granary.api.services

import cats._
import cats.effect._
import cats.syntax.all._
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.api.auth.Auth
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.datamodel._
import com.rasterfoundry.granary.database.TaskDao
import doobie._
import doobie.implicits._
import org.http4s._
import sttp.tapir.server.http4s._

import java.util.UUID

class TaskService[F[_]: Sync](
    defaultPageRequest: PageRequest,
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F],
    auth: Auth[F]
)(implicit
    contextShift: ContextShift[F]
) extends GranaryService {

  def createTask(token: Token, task: Task.Create): F[Either[CrudError, Task]] =
    mkContext("createTask", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        TaskDao.insertTask(token, task).transact(xa)
      )(Right(_))
    }

  def listTasks(
      token: Token,
      pageRequest: PageRequest
  ): F[Either[CrudError, PaginatedResponse[Task]]] = {
    val forPage = pageRequest `combine` defaultPageRequest
    mkContext("listTasks", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        TaskDao.listTasks(token, forPage).transact(xa)
      )(tasks => Right(PaginatedResponse.forRequest(tasks, forPage)))
    }
  }

  def getById(token: Token, id: UUID): F[Either[CrudError, Task]] =
    mkContext("lookupTaskById", Map("taskId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(
        TaskDao.getTask(token, id).transact(xa)
      )({
        case Some(task) => Right(task)
        case None       => Left(NotFound())
      })
    }

  def deleteById(token: Token, id: UUID): F[Either[CrudError, DeleteMessage]] =
    mkContext("deleteTaskById", Map("taskId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(TaskDao.deleteTask(token, id).transact(xa))({
        case Some(n) => Right(DeleteMessage(n))
        case None    => Left(NotFound())
      })
    }

  val create = TaskEndpoints.create
    .serverLogicPart(auth.fallbackToForbidden)
    .andThen({ case (token, rest) =>
      createTask(token, rest)
    })
    .toRoutes

  val list = TaskEndpoints.list
    .serverLogicPart(auth.fallbackToForbidden)
    .andThen({ case (token, rest) =>
      listTasks(token, rest)
    })
    .toRoutes

  val detail = TaskEndpoints.idLookup
    .serverLogicPart(auth.fallbackToForbidden)
    .andThen({ case (token, rest) =>
      getById(token, rest)
    })
    .toRoutes

  val delete = TaskEndpoints.delete
    .serverLogicPart(auth.fallbackToForbidden)
    .andThen({ case (token, rest) =>
      deleteById(token, rest)
    })
    .toRoutes

  val routes: HttpRoutes[F] = delete <+> detail <+> create <+> list
}
