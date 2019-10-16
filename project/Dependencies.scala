import scala.util.Properties

import sbt._

// Versions
object Versions {
  val CirceVersion       = "0.12.1"
  val DoobieVersion      = "0.8.2"
  val Http4sVersion      = "0.20.11"
  val LogbackVersion     = "1.2.3"
  val OpenTracingVersion = "0.1.0"
  val PureConfig         = "0.12.1"
  // val RasterFoundryVersion = "1.31.0"
  val ScapegoatVersion = "1.3.8"
  val Specs2Version    = "4.1.0"
  val TapirVersion     = "0.11.6"
}

object Dependencies {
  val circeCore       = "io.circe"              %% "circe-core"          % Versions.CirceVersion
  val circeGeneric    = "io.circe"              %% "circe-generic"       % Versions.CirceVersion
  val doobie          = "org.tpolecat"          %% "doobie-core"         % Versions.DoobieVersion
  val doobieHikari    = "org.tpolecat"          %% "doobie-hikari"       % Versions.DoobieVersion
  val doobiePostgres  = "org.tpolecat"          %% "doobie-postgres"     % Versions.DoobieVersion
  val doobieScalatest = "org.tpolecat"          %% "doobie-scalatest"    % Versions.DoobieVersion % "test"
  val doobieSpecs2    = "org.tpolecat"          %% "doobie-specs2"       % Versions.DoobieVersion % "test"
  val http4s          = "org.http4s"            %% "http4s-blaze-server" % Versions.Http4sVersion
  val http4sCirce     = "org.http4s"            %% "http4s-circe"        % Versions.Http4sVersion
  val http4sDsl       = "org.http4s"            %% "http4s-dsl"          % Versions.Http4sVersion
  val http4sServer    = "org.http4s"            %% "http4s-blaze-server" % Versions.Http4sVersion
  val logbackClassic  = "ch.qos.logback"        % "logback-classic"      % Versions.LogbackVersion
  val openTracing     = "com.colisweb"          %% "scala-opentracing"   % Versions.OpenTracingVersion
  val pureConfig      = "com.github.pureconfig" %% "pureconfig"          % Versions.PureConfig
  // val rasterFoundryHttp4s   = "com.rasterfoundry"      %% "http4s-util"              % Versions.RasterFoundryVersion
  val specs2Core            = "org.specs2"             %% "specs2-core"              % Versions.Specs2Version % "test"
  val tapir                 = "com.softwaremill.tapir" %% "tapir-core"               % Versions.TapirVersion
  val tapirCirce            = "com.softwaremill.tapir" %% "tapir-json-circe"         % Versions.TapirVersion
  val tapirHttp4sServer     = "com.softwaremill.tapir" %% "tapir-http4s-server"      % Versions.TapirVersion
  val tapirOpenAPICirceYAML = "com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % Versions.TapirVersion
  val tapirOpenAPIDocs      = "com.softwaremill.tapir" %% "tapir-openapi-docs"       % Versions.TapirVersion
  val tapirSwaggerUIHttp4s  = "com.softwaremill.tapir" %% "tapir-swagger-ui-http4s"  % Versions.TapirVersion
}
