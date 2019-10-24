package com.rasterfoundry.granary.api.services

import cats._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.database.PredictionDao
import com.rasterfoundry.granary.datamodel._
import doobie._
import doobie.implicits._
import org.http4s._
import shapeless._
import tapir.server.http4s._

import java.util.UUID

class PredictionService[F[_]: Sync](contextBuilder: TracingContextBuilder[F], xa: Transactor[F])(
    implicit contextShift: ContextShift[F]
) extends GranaryService {

  type PredictionCreationError = ValidationError :+: NotFound :+: CNil

  def listPredictions(
      modelId: Option[UUID],
      status: Option[JobStatus]
  ): F[Either[Unit, List[Prediction]]] =
    mkContext("listPredictions", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(PredictionDao.listPredictions(modelId, status).transact(xa))(Right(_))
    }

  def getById(id: UUID): F[Either[NotFound, Prediction]] =
    mkContext("lookupPredictionById", Map("predictionId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(PredictionDao.getPrediction(id).transact(xa))({
        case Some(prediction) => Right(prediction)
        case None             => Left(NotFound())
      })
    }

  def createPrediction(
      prediction: Prediction.Create
  ): F[Either[PredictionCreationError, Prediction]] =
    mkContext("createPrediction", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(PredictionDao.insertPrediction(prediction).transact(xa))({
        case Right(created) => Right(created)
        case Left(PredictionDao.ModelNotFound) =>
          Left(Coproduct[PredictionCreationError](NotFound()))
        case Left(PredictionDao.ArgumentsValidationFailed(errs)) =>
          Left(
            Coproduct[PredictionCreationError](ValidationError(errs map { _.getMessage } reduce))
          )
      })
    }

  val list   = PredictionEndpoints.list.toRoutes(Function.tupled(listPredictions))
  val detail = PredictionEndpoints.idLookup.toRoutes(getById)
  val create = PredictionEndpoints.create.toRoutes(createPrediction)

}
