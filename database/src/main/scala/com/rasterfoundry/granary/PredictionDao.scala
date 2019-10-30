package com.rasterfoundry.granary.database

import com.rasterfoundry.granary.datamodel._

import cats.data.{NonEmptyList, OptionT}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import io.circe.schema.ValidationError

import java.util.UUID

object PredictionDao {

  sealed abstract class PredictionDaoError extends Throwable
  case object ModelNotFound                extends PredictionDaoError

  case class ArgumentsValidationFailed(underlying: NonEmptyList[ValidationError])
      extends PredictionDaoError

  val selectF =
    fr"""
      SELECT
        id, model_id, invoked_at, arguments, status,
        status_reason, output_location, webhook_id
      FROM predictions
    """

  def listPredictions(
      modelId: Option[UUID],
      status: Option[JobStatus]
  ): ConnectionIO[List[Prediction]] =
    (selectF ++ Fragments.whereOrOpt(modelId map { id =>
      fr"model_id = $id"
    }, status map { s =>
      fr"status = $s"
    }))
      .query[Prediction]
      .to[List]

  def getPrediction(id: UUID): ConnectionIO[Option[Prediction]] =
    (selectF ++ Fragments.whereOr(fr"id = $id")).query[Prediction].option

  def unsafeGetPrediction(id: UUID): ConnectionIO[Prediction] =
    (selectF ++ Fragments.whereOr(fr"id = $id")).query[Prediction].unique

  def kickOffPredictionJob(prediction: Prediction, model: Model): ConnectionIO[Prediction] = {

    for {
      _ <- AWSBatch.submitJobRequest[ConnectionIO](
        model.jobDefinition,
        model.jobQueue,
        prediction.arguments,
        s"${model.name} -- ${prediction.id}"
      )
      // TODO -- update to failed if job could not be kicked off
      newStatus: JobStatus = JobStatus.Started
      updated <- (fr"update predictions set status = $newStatus" ++ Fragments.whereOr(
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
        )
    } yield updated
  }

  def insertPrediction(
      prediction: Prediction.Create
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
          case Invalid(errs) => Left(ArgumentsValidationFailed(errs)).pure[ConnectionIO]
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
      updated <- OptionT.liftF { insert traverse { kickOffPredictionJob(_, model) } }
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
