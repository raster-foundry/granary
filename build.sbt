cancelable in Global := true

lazy val commonDependencies = Seq(
  Dependencies.specs2Core,
  Dependencies.logbackClassic
)

lazy val commonSettings = Seq(
  organization := "com.rasterfoundry",
  name := "granary",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.10",
  scalafmtOnCompile := true,
  scapegoatVersion in ThisBuild := Versions.ScapegoatVersion,
  scalacOptions := Seq(
    "-Ypartial-unification",
    // Required by ScalaFix
    "-Yrangepos",
    "-language:higherKinds",
    "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-deprecation",
    "-feature"
  ),
  externalResolvers := Seq(
    DefaultMavenRepository,
    Resolver.sonatypeRepo("snapshots"),
    // Required transitively
    Resolver.bintrayRepo("guizmaii", "maven"),
    Resolver.bintrayRepo("colisweb", "maven")
  ),
  autoCompilerPlugins := true,
  addCompilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.6"),
  addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.2.4"),
  addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin(scalafixSemanticdb)
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(api, database, datamodel)

///////////////
// Datamodel //
///////////////
lazy val datamodelSettings = commonSettings ++ Seq(
  name := "datamodel",
  fork in run := true
)

lazy val datamodelDependencies = commonDependencies ++ Seq(
  Dependencies.circeCore,
  Dependencies.circeGeneric,
  Dependencies.http4s,
  Dependencies.http4sCirce
)

lazy val datamodel = (project in file("datamodel"))
  .settings(datamodelSettings: _*)
  .settings({ libraryDependencies ++= datamodelDependencies })

//////////////
// Database //
//////////////
lazy val databaseSettings = commonSettings ++ Seq(
  name := "database",
  fork in run := true
)

lazy val databaseDependencies = commonDependencies ++ Seq(
  Dependencies.doobie,
  Dependencies.doobieHikari,
  Dependencies.doobiePostgres,
  Dependencies.doobieSpecs2,
  Dependencies.doobieScalatest
)

lazy val database = (project in file("database"))
  .dependsOn(datamodel)
  .settings(databaseSettings: _*)
  .settings({
    libraryDependencies ++= databaseDependencies
  })

///////////////
//    API    //
///////////////
lazy val apiSettings = commonSettings ++ Seq(
  name := "api",
  fork in run := true,
  assemblyJarName in assembly := "granary-api-assembly.jar",
  assemblyMergeStrategy in assembly := {
    case "reference.conf"                       => MergeStrategy.concat
    case "application.conf"                     => MergeStrategy.concat
    case n if n.startsWith("META-INF/services") => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
      MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _                      => MergeStrategy.first
  }
)

lazy val apiDependencies = commonDependencies ++ databaseDependencies ++ Seq(
  Dependencies.http4s,
  Dependencies.http4sCirce,
  Dependencies.http4sDsl,
  Dependencies.http4sServer,
  Dependencies.openTracing,
  Dependencies.pureConfig,
  Dependencies.rasterFoundryHttp4s,
  Dependencies.tapir,
  Dependencies.tapirCirce,
  Dependencies.tapirHttp4sServer,
  Dependencies.tapirOpenAPICirceYAML,
  Dependencies.tapirOpenAPIDocs,
  Dependencies.tapirSwaggerUIHttp4s
)

lazy val api = (project in file("api"))
  .dependsOn(datamodel, database)
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= apiDependencies
  })
