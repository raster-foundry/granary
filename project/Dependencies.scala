import scala.util.Properties

import sbt._

// Versions
object Versions {
  val awsSDK                 = "1.11.662"
  val CatsScalacheckVersion  = "0.2.0"
  val CirceVersion           = "0.12.2"
  val CirceJsonSchemaVersion = "0.1.0"
  val DoobieVersion          = "0.8.2"
  val Flyway                 = "5.2.4"
  val Http4sVersion          = "0.21.0-M5"
  val LogbackVersion         = "1.2.3"
  val Log4CatsVersion        = "1.0.1"
  val OpenTracingVersion     = "0.1.0"
  val PureConfig             = "0.12.1"
  val ScapegoatVersion       = "1.3.8"
  val ScalacheckVersion      = "1.14.2"
  val Specs2Version          = "4.6.0"
  val TapirVersion           = "0.11.9"
}

object Dependencies {
  val awsBatch              = "com.amazonaws"          % "aws-java-sdk-batch"        % Versions.awsSDK
  val awsS3                 = "com.amazonaws"          % "aws-java-sdk-s3"           % Versions.awsSDK
  val catsScalacheck        = "io.chrisdavenport"      %% "cats-scalacheck"          % Versions.CatsScalacheckVersion % "test"
  val circeCore             = "io.circe"               %% "circe-core"               % Versions.CirceVersion
  val circeGeneric          = "io.circe"               %% "circe-generic"            % Versions.CirceVersion
  val circeJsonSchema       = "io.circe"               %% "circe-json-schema"        % Versions.CirceJsonSchemaVersion
  val doobie                = "org.tpolecat"           %% "doobie-core"              % Versions.DoobieVersion
  val doobieHikari          = "org.tpolecat"           %% "doobie-hikari"            % Versions.DoobieVersion
  val doobiePostgres        = "org.tpolecat"           %% "doobie-postgres"          % Versions.DoobieVersion
  val doobiePostgresCirce   = "org.tpolecat"           %% "doobie-postgres-circe"    % Versions.DoobieVersion
  val doobieScalatest       = "org.tpolecat"           %% "doobie-scalatest"         % Versions.DoobieVersion % "test"
  val doobieSpecs2          = "org.tpolecat"           %% "doobie-specs2"            % Versions.DoobieVersion % "test"
  val flyway                = "org.flywaydb"           % "flyway-core"               % Versions.Flyway % "test"
  val http4s                = "org.http4s"             %% "http4s-blaze-server"      % Versions.Http4sVersion
  val http4sCirce           = "org.http4s"             %% "http4s-circe"             % Versions.Http4sVersion
  val http4sDsl             = "org.http4s"             %% "http4s-dsl"               % Versions.Http4sVersion
  val http4sServer          = "org.http4s"             %% "http4s-blaze-server"      % Versions.Http4sVersion
  val logbackClassic        = "ch.qos.logback"         % "logback-classic"           % Versions.LogbackVersion
  val log4cats              = "io.chrisdavenport"      %% "log4cats-slf4j"           % Versions.Log4CatsVersion
  val openTracing           = "com.colisweb"           %% "scala-opentracing"        % Versions.OpenTracingVersion
  val pureConfig            = "com.github.pureconfig"  %% "pureconfig"               % Versions.PureConfig
  val scalacheck            = "org.scalacheck"         %% "scalacheck"               % Versions.ScalacheckVersion % "test"
  val specs2Core            = "org.specs2"             %% "specs2-core"              % Versions.Specs2Version % "test"
  val specs2Scalacheck      = "org.specs2"             %% "specs2-scalacheck"        % Versions.Specs2Version % "test"
  val tapir                 = "com.softwaremill.tapir" %% "tapir-core"               % Versions.TapirVersion
  val tapirCirce            = "com.softwaremill.tapir" %% "tapir-json-circe"         % Versions.TapirVersion
  val tapirHttp4sServer     = "com.softwaremill.tapir" %% "tapir-http4s-server"      % Versions.TapirVersion
  val tapirOpenAPICirceYAML = "com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % Versions.TapirVersion
  val tapirOpenAPIDocs      = "com.softwaremill.tapir" %% "tapir-openapi-docs"       % Versions.TapirVersion
  val tapirSwaggerUIHttp4s  = "com.softwaremill.tapir" %% "tapir-swagger-ui-http4s"  % Versions.TapirVersion
}
