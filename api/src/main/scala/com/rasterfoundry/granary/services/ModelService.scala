package com.rasterfoundry.granary.api.services

import cats._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.database.ModelDao
import com.rasterfoundry.granary.datamodel.Model
import doobie._
import doobie.implicits._
import org.http4s._
import sttp.tapir.server.http4s._

import java.util.UUID

class ModelService[F[_]: Sync](contextBuilder: TracingContextBuilder[F], xa: Transactor[F])(
    implicit contextShift: ContextShift[F]
) extends GranaryService {

  def createModel(model: Model.Create): F[Either[Unit, Model]] =
    mkContext("createModel", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(ModelDao.insertModel(model).transact(xa))(Right(_))
    }

  val listModels: Unit => F[Either[Unit, List[Model]]] =
    _ =>
      mkContext("listModels", Map.empty, contextBuilder) use { _ =>
        Functor[F].map(ModelDao.listModels.transact(xa))(Right(_))
    }

  def getById(id: UUID): F[Either[NotFound, Model]] =
    mkContext("lookupModelById", Map("modelId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(ModelDao.getModel(id).transact(xa))({
        case Some(model) => Right(model)
        case None        => Left(NotFound())
      })
    }

  def deleteById(id: UUID): F[Either[NotFound, DeleteMessage]] =
    mkContext("deleteModelById", Map("modelId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(ModelDao.deleteModel(id).transact(xa))({
        case Some(n) => Right(DeleteMessage(n))
        case None    => Left(NotFound())
      })
    }

  val create = ModelEndpoints.create.toRoutes(createModel)
  val list   = ModelEndpoints.list.toRoutes(listModels)
  val detail = ModelEndpoints.idLookup.toRoutes(getById)
  val delete = ModelEndpoints.delete.toRoutes(deleteById)

  val routes: HttpRoutes[F] = delete <+> detail <+> create <+> list
}
