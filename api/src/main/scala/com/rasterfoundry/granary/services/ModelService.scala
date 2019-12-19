package com.rasterfoundry.granary.api.services

import cats._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.api.Auth.authorized
import com.rasterfoundry.granary.database.ModelDao
import com.rasterfoundry.granary.datamodel.Model
import doobie._
import doobie.implicits._
import org.http4s._
import sttp.tapir.server.http4s._

import java.util.UUID

class ModelService[F[_]: Sync](
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F],
    authEnabled: Boolean
)(
    implicit contextShift: ContextShift[F]
) extends GranaryService {

  def createModel(model: Model.Create, tokenO: Option[String]): F[Either[CrudError, Model]] =
    mkContext("createModel", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        authorized(tokenO, authEnabled, ModelDao.insertModel(model)).transact(xa)
      ) {
        case Right(r) => Right(r)
        case Left(l)  => Left(l)
      }
    }

  def listModels(tokenO: Option[String]): F[Either[CrudError, List[Model]]] = {
    mkContext("listModels", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        authorized(tokenO, authEnabled, ModelDao.listModels).transact(xa)
      ) {
        case Right(r) => Right(r)
        case Left(l)  => Left(l)
      }
    }
  }

  def getById(id: UUID, tokenO: Option[String]): F[Either[CrudError, Model]] =
    mkContext("lookupModelById", Map("modelId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(
        authorized(tokenO, authEnabled, ModelDao.getModel(id)).transact(xa)
      )({
        case Right(Some(model)) => Right(model)
        case Right(None)        => Left(NotFound())
        case Left(l)            => Left(l)
      })
    }

  def deleteById(id: UUID, tokenO: Option[String]): F[Either[CrudError, DeleteMessage]] =
    mkContext("deleteModelById", Map("modelId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(authorized(tokenO, authEnabled, ModelDao.deleteModel(id)).transact(xa))({
        case Right(Some(n)) => Right(DeleteMessage(n))
        case Right(None)    => Left(NotFound())
        case Left(l)        => Left(l)
      })
    }

  val create = ModelEndpoints.create.toRoutes(Function.tupled(createModel))
  val list   = ModelEndpoints.list.toRoutes(listModels)
  val detail = ModelEndpoints.idLookup.toRoutes(Function.tupled(getById))
  val delete = ModelEndpoints.delete.toRoutes(Function.tupled(deleteById))

  val routes: HttpRoutes[F] = delete <+> detail <+> create <+> list
}
