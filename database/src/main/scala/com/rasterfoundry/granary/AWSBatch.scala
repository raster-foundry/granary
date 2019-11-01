package com.rasterfoundry.granary.database

import cats.effect._
import cats.implicits._
import com.amazonaws.services.batch.AWSBatchClientBuilder
import com.amazonaws.services.batch.model.SubmitJobRequest
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.{Json}

import scala.collection.JavaConverters._

object AWSBatch {
  val batchClient = AWSBatchClientBuilder.defaultClient()

  implicit def unsafeLogger = Slf4jLogger.getLogger[IO]

  def submitJobRequest[F[_]: LiftIO: Sync](
      jobDefinition: String,
      jobQueueName: String,
      parameters: Json,
      jobName: String
  ): F[Either[Throwable, Unit]] = LiftIO[F].liftIO {
    parameters.as[Map[String, String]] traverse { params =>
      val jobRequest = new SubmitJobRequest()
        .withJobName(jobName)
        .withJobDefinition(jobDefinition)
        .withJobQueue(jobQueueName)
        .withParameters(params.asJava)

      val runJob = Config.environment.toUpperCase() === "PRODUCTION"

      (if (runJob) {
         IO {
           batchClient.submitJob(jobRequest)
         }
       } else Logger[IO].info("Not running job because in development")).attempt
    } map {
      case Left(e)         => Left(e)
      case Right(Left(e))  => Left(e)
      case Right(Right(r)) =>
        // trick to suppress "pure expression in statement position" warning
        Right(locally(r))
    }
  }
}