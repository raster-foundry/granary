package com.rasterfoundry.granary.database

import cats.effect._
import cats.implicits._
import com.amazonaws.services.batch.AWSBatchClientBuilder
import com.amazonaws.services.batch.model.SubmitJobRequest
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Json

import scala.collection.JavaConverters._

object AWSBatch {

  val batchClient = AWSBatchClientBuilder.defaultClient()
  val s3Client    = AmazonS3ClientBuilder.defaultClient()

  implicit def unsafeLogger = Slf4jLogger.getLogger[IO]

  def submitJobRequest[F[_]: LiftIO: Sync](
      jobDefinition: String,
      jobQueueName: String,
      parameters: Json,
      jobName: String,
      dataBucket: String
  ): F[Either[Throwable, Unit]] = LiftIO[F].liftIO {
    parameters.as[Map[String, String]] traverse { params =>
      val updatedParametersIO: IO[Map[String, String]] = params.get("TASKGRID") match {
        case Some(v) =>
          IO {
            val key = s"prediction-task-grids/$jobName/taskGrid.json"
            s3Client.putObject(dataBucket, key, v)
            params.updated("TASKGRID", s"s3://${dataBucket}/$key")
          }
        case _ => IO.pure(params)
      }

      val jobRequestIO = updatedParametersIO map { updatedParameters =>
        new SubmitJobRequest()
          .withJobName(jobName)
          .withJobDefinition(jobDefinition)
          .withJobQueue(jobQueueName)
          .withParameters(updatedParameters.asJava)
      }

      val runJob = Config.environment.toUpperCase() === "PRODUCTION"

      jobRequestIO flatMap { jobRequest =>
        (if (runJob) {
           IO {
             batchClient.submitJob(jobRequest)
           }
         } else Logger[IO].debug("Not running job because in development")).attempt
      }
    } map {
      case Left(e)         => Left(e)
      case Right(Left(e))  => Left(e)
      case Right(Right(r)) =>
        // trick to suppress "pure expression in statement position" warning
        Right(locally(r))
    }
  }
}
