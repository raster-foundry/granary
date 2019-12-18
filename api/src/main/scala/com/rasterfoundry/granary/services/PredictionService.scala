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
import com.rasterfoundry.granary.api.Auth.authorized

class PredictionService[F[_]: Sync](
    contextBuilder: TracingContextBuilder[F],
    xa: Transactor[F],
    dataBucket: String,
    apiHost: String,
    authEnabled: Boolean
)(
    implicit contextShift: ContextShift[F]
) extends GranaryService {
  private val s3Client = AmazonS3ClientBuilder.defaultClient()

  def listPredictions(
      modelId: Option[UUID],
      status: Option[JobStatus],
      tokenO: Option[String]
  ): F[Either[CrudError, List[Prediction]]] =
    mkContext("listPredictions", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        authorized(
          tokenO,
          authEnabled,
          PredictionDao.listPredictions(modelId, status)
        ).transact(xa)
      ) { predictions =>
        predictions match {
          case Right(pred) =>
            val updatedPredictions = pred.map(_.signS3OutputLocation(s3Client))
            Right(updatedPredictions)
          case l => l
        }
      }
    }

  def getById(id: UUID, tokenO: Option[String]): F[Either[CrudError, Prediction]] =
    mkContext("lookupPredictionById", Map("predictionId" -> s"$id"), contextBuilder) use { _ =>
      Functor[F].map(
        authorized(tokenO, authEnabled, PredictionDao.getPrediction(id)).transact(xa)
      ) {
        case Right(Some(prediction)) =>
          Right(prediction.signS3OutputLocation(s3Client))
        case Right(None) => Left(NotFound())
        case Left(l)     => Left(l)
      }
    }

  def createPrediction(
      prediction: Prediction.Create,
      tokenO: Option[String]
  ): F[Either[CrudError, Prediction]] =
    mkContext("createPrediction", Map.empty, contextBuilder) use { _ =>
      Functor[F].map(
        authorized(
          tokenO,
          authEnabled,
          PredictionDao.insertPrediction(prediction, dataBucket, apiHost)
        ).transact(xa)
      )({
        case Right(Right(created)) => Right(created)
        case Right(Left(PredictionDao.ModelNotFound)) =>
          Left(NotFound())
        case Right(Left(PredictionDao.ArgumentsValidationFailed(errs))) =>
          Left(
            ValidationError(errs map { _.getMessage } reduce)
          )
        case Right(Left(PredictionDao.BatchSubmissionFailed(msg))) =>
          Left(
            ValidationError(
              s"Batch resources for model ${prediction.modelId} may be misconfigured: $msg"
            )
          )
        case Left(l) => Left(l)
      })
    }

  def addPredictionResults(
      predictionId: UUID,
      predictionWebhookId: UUID,
      updateMessage: PredictionStatusUpdate,
      tokenO: Option[String]
  ): F[Either[CrudError, Prediction]] =
    mkContext(
      "addPredictionResults",
      Map("statusUpdate" -> updateMessage.asJson.noSpaces),
      contextBuilder
    ) use { _ =>
      Functor[F].map(
        authorized(
          tokenO,
          authEnabled,
          PredictionDao
            .addResults(predictionId, predictionWebhookId, updateMessage)
            .value
        ).transact(xa)
      )({
        case Right(None) =>
          Left(NotFound())
        case Right(Some(p)) =>
          Right(p.signS3OutputLocation(s3Client))
        case Left(l) => Left(l)
      })
    }

  val list       = PredictionEndpoints.list.toRoutes(Function.tupled(listPredictions))
  val detail     = PredictionEndpoints.idLookup.toRoutes(Function.tupled(getById))
  val create     = PredictionEndpoints.create.toRoutes(Function.tupled(createPrediction))
  val addResults = PredictionEndpoints.addResults.toRoutes(Function.tupled(addPredictionResults))

  val routes: HttpRoutes[F] = detail <+> create <+> list <+> addResults

}
