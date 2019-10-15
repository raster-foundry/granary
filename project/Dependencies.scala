import scala.util.Properties

import sbt._

// Versions
object Versions {
  val Http4sVersion = "0.20.11"
  val Specs2Version = "4.1.0"
  val LogbackVersion = "1.2.3"
  val ScapegoatVersion = "1.3.8"
  val CirceVersion = "0.12.1"
  val DoobieVersion = "0.8.2"
}

object Dependencies {
  val circeCore        = "io.circe"                   %% "circe-core"                     % Versions.CirceVersion
  val circeGeneric     = "io.circe"                   %% "circe-generic"                  % Versions.CirceVersion
  val doobie           = "org.tpolecat"               %% "doobie-core"                    % Versions.DoobieVersion
  val doobieHikari     = "org.tpolecat"               %% "doobie-hikari"                    % Versions.DoobieVersion
  val doobiePostgres   = "org.tpolecat"               %% "doobie-postgres"                % Versions.DoobieVersion
  val doobieSpecs2     = "org.tpolecat"               %% "doobie-specs2"                  % Versions.DoobieVersion % "test"
  val doobieScalatest  = "org.tpolecat"               %% "doobie-scalatest"               % Versions.DoobieVersion % "test"
  val http4s           = "org.http4s"                 %% "http4s-blaze-server"            % Versions.Http4sVersion
  val http4sCirce      = "org.http4s"                 %% "http4s-circe"                   % Versions.Http4sVersion
  val http4sServer     = "org.http4s"                 %% "http4s-blaze-server"            % Versions.Http4sVersion
  val http4sDsl        = "org.http4s"                 %% "http4s-dsl"                     % Versions.Http4sVersion
  val logbackClassic   = "ch.qos.logback"             % "logback-classic"                 % Versions.LogbackVersion
  val specs2Core       = "org.specs2"                 %% "specs2-core"                    % Versions.Specs2Version % "test"
}
