import scala.util.Properties

import sbt._

// Versions
object Versions {
  val awsSDK                 = "1.11.833"
  val CatsEffectVersion      = "2.1.4"
  val CatsVersion            = "2.1.1"
  val CatsScalacheckVersion  = "0.3.0"
  val CirceVersion           = "0.13.0"
  val CirceJsonSchemaVersion = "0.1.0"
  val DeclineVersion         = "1.2.0"
  val DoobieVersion          = "0.9.0"
  val Flyway                 = "6.5.3"
  val HikariVersion          = "3.4.5"
  val Http4sVersion          = "0.21.6"
  val LogbackVersion         = "1.2.3"
  val Log4CatsVersion        = "1.1.1"
  val MagnoliaVersion        = "0.16.0"
  val NewtypeVersion         = "0.4.4"
  val OpenTracingVersion     = "0.1.1"
  val PureConfig             = "0.13.0"
  val RefinedVersion         = "0.9.15"
  val ScapegoatVersion       = "1.3.11"
  val ScalacheckVersion      = "1.14.3"
  val ShapelessVersion       = "2.3.3"
  val Slf4jVersion           = "1.7.30"
  val SourceCodeVersion      = "0.2.1"
  val Specs2Version          = "4.10.2"
  val Stac4s                 = "0.0.12-3-g699a2cf"
  val SttpVersion            = "1.1.4"
  val TapirVersion           = "0.16.10"
}

object Dependencies {
  val awsBatch   = "com.amazonaws"  % "aws-java-sdk-batch" % Versions.awsSDK
  val awsCore    = "com.amazonaws"  % "aws-java-sdk-core"  % Versions.awsSDK
  val awsS3      = "com.amazonaws"  % "aws-java-sdk-s3"    % Versions.awsSDK
  val catsCore   = "org.typelevel" %% "cats-core"          % Versions.CatsVersion
  val catsEffect = "org.typelevel" %% "cats-effect"        % Versions.CatsEffectVersion
  val catsFree   = "org.typelevel" %% "cats-free"          % Versions.CatsVersion
  val catsKernel = "org.typelevel" %% "cats-kernel"        % Versions.CatsVersion

  val catsScalacheck =
    "io.chrisdavenport" %% "cats-scalacheck" % Versions.CatsScalacheckVersion % "test"
  val circeCore           = "io.circe"                    %% "circe-core"            % Versions.CirceVersion
  val circeGeneric        = "io.circe"                    %% "circe-generic"         % Versions.CirceVersion
  val circeJsonSchema     = "io.circe"                    %% "circe-json-schema"     % Versions.CirceJsonSchemaVersion
  val circeLiteral        = "io.circe"                    %% "circe-literal"         % Versions.CirceVersion
  val circeNumbers        = "io.circe"                    %% "circe-numbers"         % Versions.CirceVersion
  val circeRefined        = "io.circe"                    %% "circe-refined"         % Versions.CirceVersion
  val decline             = "com.monovore"                %% "decline"               % Versions.DeclineVersion
  val declineRefined      = "com.monovore"                %% "decline-refined"       % Versions.DeclineVersion
  val doobie              = "org.tpolecat"                %% "doobie-core"           % Versions.DoobieVersion
  val doobieFree          = "org.tpolecat"                %% "doobie-free"           % Versions.DoobieVersion
  val doobieHikari        = "org.tpolecat"                %% "doobie-hikari"         % Versions.DoobieVersion
  val doobiePostgres      = "org.tpolecat"                %% "doobie-postgres"       % Versions.DoobieVersion
  val doobiePostgresCirce = "org.tpolecat"                %% "doobie-postgres-circe" % Versions.DoobieVersion
  val doobieRefined       = "org.tpolecat"                %% "doobie-refined"        % Versions.DoobieVersion
  val doobieScalatest     = "org.tpolecat"                %% "doobie-scalatest"      % Versions.DoobieVersion     % "test"
  val doobieSpecs2        = "org.tpolecat"                %% "doobie-specs2"         % Versions.DoobieVersion     % "test"
  val flyway              = "org.flywaydb"                 % "flyway-core"           % Versions.Flyway            % "test"
  val hikariCP            = "com.zaxxer"                   % "HikariCP"              % Versions.HikariVersion
  val http4sCirce         = "org.http4s"                  %% "http4s-circe"          % Versions.Http4sVersion
  val http4sCore          = "org.http4s"                  %% "http4s-core"           % Versions.Http4sVersion
  val http4sDsl           = "org.http4s"                  %% "http4s-dsl"            % Versions.Http4sVersion
  val http4sServer        = "org.http4s"                  %% "http4s-server"         % Versions.Http4sVersion
  val http4sBlazeServer   = "org.http4s"                  %% "http4s-blaze-server"   % Versions.Http4sVersion
  val logbackClassic      = "ch.qos.logback"               % "logback-classic"       % Versions.LogbackVersion
  val log4catsCore        = "io.chrisdavenport"           %% "log4cats-core"         % Versions.Log4CatsVersion
  val log4catsSlf4j       = "io.chrisdavenport"           %% "log4cats-slf4j"        % Versions.Log4CatsVersion
  val magnolia            = "com.propensive"              %% "magnolia"              % Versions.MagnoliaVersion
  val newtype             = "io.estatico"                 %% "newtype"               % Versions.NewtypeVersion
  val openTracing         = "com.colisweb"                %% "scala-opentracing"     % Versions.OpenTracingVersion
  val pureConfigCore      = "com.github.pureconfig"       %% "pureconfig-core"       % Versions.PureConfig
  val pureConfigGeneric   = "com.github.pureconfig"       %% "pureconfig-generic"    % Versions.PureConfig
  val refined             = "eu.timepit"                  %% "refined"               % Versions.RefinedVersion
  val refinedPureconfig   = "eu.timepit"                  %% "refined-pureconfig"    % Versions.RefinedVersion
  val scalacheck          = "org.scalacheck"              %% "scalacheck"            % Versions.ScalacheckVersion % "test"
  val shapeless           = "com.chuusai"                 %% "shapeless"             % Versions.ShapelessVersion
  val slf4jApi            = "org.slf4j"                    % "slf4j-api"             % Versions.Slf4jVersion
  val sourceCode          = "com.lihaoyi"                 %% "sourcecode"            % Versions.SourceCodeVersion
  val specs2Core          = "org.specs2"                  %% "specs2-core"           % Versions.Specs2Version     % "test"
  val specs2Scalacheck    = "org.specs2"                  %% "specs2-scalacheck"     % Versions.Specs2Version     % "test"
  val stac4s              = "com.azavea.stac4s"           %% "core"                  % Versions.Stac4s
  val sttpModel           = "com.softwaremill.sttp.model" %% "core"                  % Versions.SttpVersion
  val tapir               = "com.softwaremill.sttp.tapir" %% "tapir-core"            % Versions.TapirVersion
  val tapirCirce          = "com.softwaremill.sttp.tapir" %% "tapir-json-circe"      % Versions.TapirVersion

  val tapirOpenAPIModel =
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-model" % Versions.TapirVersion
  val tapirRefined = "com.softwaremill.sttp.tapir" %% "tapir-refined" % Versions.TapirVersion

  val tapirHttp4sServer =
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % Versions.TapirVersion

  val tapirOpenAPICirceYAML =
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % Versions.TapirVersion

  val tapirOpenAPIDocs =
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % Versions.TapirVersion

  val tapirSwaggerUIHttp4s =
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s" % Versions.TapirVersion
}
