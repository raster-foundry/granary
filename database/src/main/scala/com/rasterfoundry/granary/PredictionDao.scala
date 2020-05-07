package com.rasterfoundry.granary.database

import com.rasterfoundry.granary.datamodel._

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.amazonaws.services.batch.model.ClientException
import doobie._
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import io.circe.DecodingFailure
import io.circe.schema.ValidationError
import io.circe.syntax._

import java.util.UUID

object PredictionDao {

  sealed abstract class PredictionDaoError extends Throwable
  case object ModelNotFound                extends PredictionDaoError

  case class ArgumentsValidationFailed(underlying: NonEmptyList[ValidationError])
      extends PredictionDaoError
  case class BatchSubmissionFailed(msg: String) extends PredictionDaoError

  @inline def batchSafeJobName(s: String): String =
    s.replace(" ", "-")

  val selectF =
    fr"""
      SELECT
        id, model_id, invoked_at, arguments, status,
        status_reason, output_location, webhook_id
      FROM predictions
    """

  def listPredictions(
      pageRequest: PageRequest,
      modelId: Option[UUID],
      status: Option[JobStatus]
  ): ConnectionIO[List[Prediction]] =
    Page(
      selectF ++ Fragments.whereOrOpt(
        modelId map { id => fr"model_id = $id" },
        status map { s =>
          fr"status = $s"
        }
      ),
      pageRequest
    ).query[Prediction]
      .to[List]

  def getPrediction(id: UUID): ConnectionIO[Option[Prediction]] =
    (selectF ++ Fragments.whereOr(fr"id = $id")).query[Prediction].option

  def unsafeGetPrediction(id: UUID): ConnectionIO[Prediction] =
    (selectF ++ Fragments.whereOr(fr"id = $id")).query[Prediction].unique

  def kickOffPredictionJob(
      prediction: Prediction,
      model: Model,
      dataBucket: String,
      apiHost: String
  ): EitherT[ConnectionIO, PredictionDaoError, Prediction] = {

    def updateFailure(
        prediction: Prediction
    ): Throwable => EitherT[ConnectionIO, PredictionDaoError, Prediction] =
      (e: Throwable) => {
        val resultErr = e match {
          case err: DecodingFailure =>
            PredictionDao.BatchSubmissionFailed(
              s"Could not decode arguments to Map[String, String] for prediction ${prediction.id}: ${err.getMessage}"
            )
          case err: ClientException =>
            PredictionDao.BatchSubmissionFailed(
              s"Client exception with AWS batch for prediction ${prediction.id}: ${err.getMessage}"
            )
        }
        val newPrediction =
          prediction.copy(status = JobStatus.Failed, statusReason = Some(e.getMessage))
        EitherT {
          (fr"""
          update predictions
          set status = ${newPrediction.status}, status_reason=${newPrediction.statusReason}
        """ ++ Fragments.whereOr(fr"id = ${prediction.id}")).update.run map { _ => Left(resultErr) }
        }
      }

    def updateSuccess(
        prediction: Prediction
    )(u: Unit): EitherT[ConnectionIO, PredictionDaoError, Prediction] = {
      // trick to suppress "pure expression in statement position" warning
      locally(u)
      val newStatus: JobStatus = JobStatus.Started
      EitherT {
        (fr"update predictions set status = $newStatus" ++ Fragments.whereOr(
          fr"id = ${prediction.id}"
        )).update
          .withUniqueGeneratedKeys[Prediction](
            "id",
            "model_id",
            "invoked_at",
            "arguments",
            "status",
            "status_reason",
            "output_location",
            "webhook_id"
          ) map { Right(_) }
      }
    }

    EitherT(
      AWSBatch
        .submitJobRequest[ConnectionIO](
          model.jobDefinition,
          model.jobQueue,
          prediction.arguments
            .deepMerge(
              prediction.webhookId map { webhookId =>
                Map(
                  "WEBHOOK_URL" -> s"${apiHost}/predictions/${prediction.id}/results/${webhookId}"
                ).asJson
              } getOrElse { ().asJson }
            ),
          batchSafeJobName(s"${model.name}-${prediction.id}"),
          dataBucket
        )
    ).biflatMap(updateFailure(prediction), updateSuccess(prediction))
  }

  def insertPrediction(
      prediction: Prediction.Create,
      dataBucket: String,
      apiHost: String
  ): ConnectionIO[Either[PredictionDaoError, Prediction]] = {
    val fragment = fr"""
      INSERT INTO predictions
        (id, model_id, invoked_at, arguments, status, status_reason, output_location, webhook_id)
      VALUES
        (uuid_generate_v4(), ${prediction.modelId}, now(), ${prediction.arguments},
        'CREATED', NULL, NULL, uuid_generate_v4())
    """
    val insertIO: OptionT[ConnectionIO, Either[PredictionDaoError, Prediction]] = for {
      model <- OptionT { ModelDao.getModel(prediction.modelId) }
      argCheck = model.validator.validate(prediction.arguments)
      insert <- OptionT.liftF {
        argCheck match {
          case Invalid(errs) =>
            Left(ArgumentsValidationFailed(errs): PredictionDaoError).pure[ConnectionIO]
          case Valid(()) =>
            fragment.update.withUniqueGeneratedKeys[Prediction](
              "id",
              "model_id",
              "invoked_at",
              "arguments",
              "status",
              "status_reason",
              "output_location",
              "webhook_id"
            ) map { Right(_) }
        }
      }
      updated <- OptionT.liftF {
        (EitherT.fromEither[ConnectionIO](insert) flatMap {
          kickOffPredictionJob(_, model, dataBucket, apiHost)
        }).value
      }
    } yield updated

    insertIO.value map {
      case Some(res) => res
      case None      => Left(ModelNotFound)
    }
  }

  def addResults(
      predictionId: UUID,
      webhookId: UUID,
      status: PredictionStatusUpdate
  ): OptionT[ConnectionIO, Prediction] =
    for {
      existingPrediction <- OptionT(getPrediction(predictionId)) flatMap {
        case pred if pred.webhookId == Some(webhookId) => OptionT.some(pred)
        case _                                         => OptionT.none[ConnectionIO, Prediction]
      }
      newPrediction = status match {
        case PredictionSuccess(output) =>
          existingPrediction.copy(
            status = JobStatus.Successful,
            outputLocation = Some(output),
            webhookId = None
          )
        case PredictionFailure(reason) =>
          existingPrediction.copy(
            status = JobStatus.Failed,
            statusReason = Some(reason),
            webhookId = None
          )
      }
      update <- OptionT.liftF {
        fr"""
        UPDATE predictions
        SET
          status = ${newPrediction.status},
          status_reason = ${newPrediction.statusReason},
          output_location = ${newPrediction.outputLocation},
          webhook_id = ${newPrediction.webhookId}
        WHERE
          id = ${predictionId}
      """.update.withUniqueGeneratedKeys[Prediction](
          "id",
          "model_id",
          "invoked_at",
          "arguments",
          "status",
          "status_reason",
          "output_location",
          "webhook_id"
        )
      }
    } yield update
}
