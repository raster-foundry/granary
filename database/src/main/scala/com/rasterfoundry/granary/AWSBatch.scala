package com.rasterfoundry.granary.database

import cats.effect._
import cats.implicits._
import com.amazonaws.services.batch.AWSBatchClientBuilder
import com.amazonaws.services.batch.model.SubmitJobRequest
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Json

import scala.collection.JavaConverters._

object AWSBatch {

  val batchClient = AWSBatchClientBuilder.defaultClient()
  val s3Client    = AmazonS3ClientBuilder.defaultClient()

  implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def submitJobRequest[F[_]: LiftIO: Sync](
      jobDefinition: String,
      jobQueueName: String,
      parameters: Json,
      jobName: String,
      dataBucket: String
  ): F[Either[Throwable, Unit]] = LiftIO[F].liftIO {
    parameters.as[Map[String, Json]] traverse { params =>
      val updatedParametersIO: IO[Map[String, String]] = params.get("TASK_GRID") match {
        case Some(v) =>
          IO {
            val key = s"prediction-task-grids/$jobName/taskGrid.json"
            s3Client.putObject(dataBucket, key, v.noSpaces)
            val updated =
              params
                .mapValues(_.noSpaces.replace("\"", ""))
                .updated("TASK_GRID", s"s3://${dataBucket}/$key")
            updated
          }
        case _ => IO.pure(params.mapValues(_.noSpaces.replace("\"", "")))
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
         } else
           Logger[IO].debug(
             s"Not running job because in development. Parameters to be sent: ${jobRequest.getParameters}")).attempt
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
