package com.rasterfoundry.granary.api.services

import java.util.UUID

import cats._
import cats.effect._
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.colisweb.tracing.TracingContextBuilder
import com.rasterfoundry.granary.api.endpoints._
import com.rasterfoundry.granary.api.error._
import com.rasterfoundry.granary.database.PredictionDao
import com.rasterfoundry.granary.datamodel._
import doobie._
import doobie.implicits._
import io.circe.syntax._
import org.http4s._
import sttp.tapir.server.http4s._

class PredictionService[F[_]: Sync](
    defaultPageRequest: PageRequest,
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F],
    dataBucket: String,
    apiHost: String
)(implicit
    contextShift: ContextShift[F]
) extends GranaryService {
  private val s3Client = AmazonS3ClientBuilder.defaultClient()

  def listPredictions(
      pageRequest: PageRequest,
      taskId: Option[UUID],
      status: Option[JobStatus]
  ): F[Either[Unit, PaginatedResponse[Prediction]]] = {
    val forPage = pageRequest `combine` defaultPageRequest
    mkContext("listPredictions", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        PredictionDao
          .listPredictions(forPage, taskId, status)
          .transact(xa)
      ) { predictions =>
        val updatedPredictions = predictions.map(_.signS3OutputLocation(s3Client))
        Right(PaginatedResponse.forRequest(updatedPredictions, forPage))
      }
    }
  }

  def getById(id: UUID): F[Either[CrudError, Prediction]] =
    mkContext("lookupPredictionById", Map("predictionId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(
        PredictionDao.getPrediction(id).transact(xa)
      ) {
        case Some(prediction) =>
          Right(prediction.signS3OutputLocation(s3Client))
        case None => Left(NotFound())
      }
    }

  def createPrediction(
      prediction: Prediction.Create
  ): F[Either[CrudError, Prediction]] =
    mkContext("createPrediction", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        PredictionDao
          .insertPrediction(prediction, dataBucket, apiHost)
          .transact(xa)
      )({
        case Right(created)                   => Right(created)
        case Left(PredictionDao.TaskNotFound) => Left(NotFound())
        case Left(PredictionDao.ArgumentsValidationFailed(errs)) =>
          Left(ValidationError(errs map { _.getMessage } reduce))
        case Left(PredictionDao.BatchSubmissionFailed(msg)) =>
          Left(
            ValidationError(
              s"Batch resources for task ${prediction.taskId} may be misconfigured: $msg"
            )
          )
      })
    }

  def addPredictionResults(
      predictionId: UUID,
      predictionWebhookId: UUID,
      updateMessage: PredictionStatusUpdate
  ): F[Either[CrudError, Prediction]] =
    mkContext(
      "addPredictionResults",
      Map("statusUpdate" -> updateMessage.asJson.noSpaces),
      contextBuilder
    ) use { _ =>
      Functor[F].map(
        PredictionDao
          .addResults(predictionId, predictionWebhookId, updateMessage)
          .value
          .transact(xa)
      )({
        case None =>
          Left(NotFound())
        case Some(p) =>
          Right(p.signS3OutputLocation(s3Client))
      })
    }

  val list   = PredictionEndpoints.list.toRoutes(Function.tupled(listPredictions))
  val detail = PredictionEndpoints.idLookup.toRoutes(getById)
  val create = PredictionEndpoints.create.toRoutes(createPrediction)

  val addResultsRoutes =
    PredictionEndpoints.addResults.toRoutes(Function.tupled(addPredictionResults))

  val routes: HttpRoutes[F] = detail <+> create <+> list

}
