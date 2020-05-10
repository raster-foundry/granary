import scala.util.Properties

import sbt._

// Versions
object Versions {
  val awsSDK                 = "1.11.778"
  val CatsScalacheckVersion  = "0.2.0"
  val CirceVersion           = "0.13.0"
  val CirceJsonSchemaVersion = "0.1.0"
  val DoobieVersion          = "0.9.0"
  val Flyway                 = "6.4.1"
  val Http4sVersion          = "0.21.3"
  val LogbackVersion         = "1.2.3"
  val Log4CatsVersion        = "1.0.1"
  val OpenTracingVersion     = "0.1.1"
  val PureConfig             = "0.12.3"
  val RefinedVersion         = "0.9.14"
  val ScapegoatVersion       = "1.3.11"
  val ScalacheckVersion      = "1.14.3"
  val Specs2Version          = "4.9.4"
  val Stac4s                 = "0.0.7-bugfix2"
  val TapirVersion           = "0.14.5"
}

object Dependencies {
  val awsBatch = "com.amazonaws" % "aws-java-sdk-batch" % Versions.awsSDK
  val awsS3    = "com.amazonaws" % "aws-java-sdk-s3"    % Versions.awsSDK

  val catsScalacheck =
    "io.chrisdavenport" %% "cats-scalacheck" % Versions.CatsScalacheckVersion % "test"
  val circeCore           = "io.circe"                    %% "circe-core"            % Versions.CirceVersion
  val circeGeneric        = "io.circe"                    %% "circe-generic"         % Versions.CirceVersion
  val circeJsonSchema     = "io.circe"                    %% "circe-json-schema"     % Versions.CirceJsonSchemaVersion
  val circeLiteral        = "io.circe"                    %% "circe-literal"         % Versions.CirceVersion
  val circeRefined        = "io.circe"                    %% "circe-refined"         % Versions.CirceVersion
  val doobie              = "org.tpolecat"                %% "doobie-core"           % Versions.DoobieVersion
  val doobieHikari        = "org.tpolecat"                %% "doobie-hikari"         % Versions.DoobieVersion
  val doobiePostgres      = "org.tpolecat"                %% "doobie-postgres"       % Versions.DoobieVersion
  val doobiePostgresCirce = "org.tpolecat"                %% "doobie-postgres-circe" % Versions.DoobieVersion
  val doobieScalatest     = "org.tpolecat"                %% "doobie-scalatest"      % Versions.DoobieVersion     % "test"
  val doobieSpecs2        = "org.tpolecat"                %% "doobie-specs2"         % Versions.DoobieVersion     % "test"
  val flyway              = "org.flywaydb"                 % "flyway-core"           % Versions.Flyway            % "test"
  val http4s              = "org.http4s"                  %% "http4s-blaze-server"   % Versions.Http4sVersion
  val http4sCirce         = "org.http4s"                  %% "http4s-circe"          % Versions.Http4sVersion
  val http4sDsl           = "org.http4s"                  %% "http4s-dsl"            % Versions.Http4sVersion
  val http4sServer        = "org.http4s"                  %% "http4s-blaze-server"   % Versions.Http4sVersion
  val logbackClassic      = "ch.qos.logback"               % "logback-classic"       % Versions.LogbackVersion
  val log4cats            = "io.chrisdavenport"           %% "log4cats-slf4j"        % Versions.Log4CatsVersion
  val openTracing         = "com.colisweb"                %% "scala-opentracing"     % Versions.OpenTracingVersion
  val pureConfig          = "com.github.pureconfig"       %% "pureconfig"            % Versions.PureConfig
  val refined             = "eu.timepit"                  %% "refined"               % Versions.RefinedVersion
  val refinedPureconfig   = "eu.timepit"                  %% "refined-pureconfig"    % Versions.RefinedVersion
  val scalacheck          = "org.scalacheck"              %% "scalacheck"            % Versions.ScalacheckVersion % "test"
  val specs2Core          = "org.specs2"                  %% "specs2-core"           % Versions.Specs2Version     % "test"
  val specs2Scalacheck    = "org.specs2"                  %% "specs2-scalacheck"     % Versions.Specs2Version     % "test"
  val stac4s              = "com.azavea.stac4s"           %% "core"                  % Versions.Stac4s
  val tapir               = "com.softwaremill.sttp.tapir" %% "tapir-core"            % Versions.TapirVersion
  val tapirCirce          = "com.softwaremill.sttp.tapir" %% "tapir-json-circe"      % Versions.TapirVersion
  val tapirRefined        = "com.softwaremill.sttp.tapir" %% "tapir-refined"         % Versions.TapirVersion

  val tapirHttp4sServer =
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % Versions.TapirVersion

  val tapirOpenAPICirceYAML =
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % Versions.TapirVersion

  val tapirOpenAPIDocs =
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % Versions.TapirVersion

  val tapirSwaggerUIHttp4s =
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s" % Versions.TapirVersion
}
