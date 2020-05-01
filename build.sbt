cancelable in Global := true

lazy val commonDependencies = Seq(
  Dependencies.specs2Core,
  Dependencies.specs2Scalacheck,
  Dependencies.logbackClassic
)

lazy val commonSettings = Seq(
  organization := "com.rasterfoundry",
  name := "granary",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.10",
  scalafmtOnCompile := true,
  scapegoatVersion in ThisBuild := Versions.ScapegoatVersion,
  externalResolvers := Seq(
    DefaultMavenRepository,
    Resolver.sonatypeRepo("snapshots"),
    // Required transitively
    Resolver.bintrayRepo("guizmaii", "maven"),
    Resolver.bintrayRepo("colisweb", "maven"),
    "jitpack".at("https://jitpack.io")
  ),
  autoCompilerPlugins := true,
  addCompilerPlugin("org.typelevel"   %% "kind-projector"     % "0.10.3"),
  addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.1" cross CrossVersion.full),
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
  Dependencies.awsS3,
  Dependencies.catsScalacheck,
  Dependencies.circeCore,
  Dependencies.circeGeneric,
  Dependencies.circeJsonSchema,
  Dependencies.circeRefined,
  Dependencies.http4s,
  Dependencies.http4sCirce,
  Dependencies.refined,
  Dependencies.scalacheck
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
  Dependencies.awsBatch,
  Dependencies.awsS3,
  Dependencies.doobie,
  Dependencies.doobieHikari,
  Dependencies.doobiePostgres,
  Dependencies.doobiePostgresCirce,
  Dependencies.doobieSpecs2,
  Dependencies.doobieScalatest,
  Dependencies.flyway,
  Dependencies.pureConfig,
  Dependencies.log4cats
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
  test in assembly := {},
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
  Dependencies.log4cats,
  Dependencies.openTracing,
  Dependencies.pureConfig,
  Dependencies.refined,
  Dependencies.refinedPureconfig,
  Dependencies.tapir,
  Dependencies.tapirCirce,
  Dependencies.tapirHttp4sServer,
  Dependencies.tapirOpenAPICirceYAML,
  Dependencies.tapirOpenAPIDocs,
  Dependencies.tapirRefined,
  Dependencies.tapirSwaggerUIHttp4s
)

lazy val api = (project in file("api"))
  .dependsOn(datamodel % "compile->compile;test->test", database % "compile->compile;test->test")
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= apiDependencies
  })

lazy val docs = project // new documentation project
  .in(file("granary-docs")) // important: it must not be docs/
  .dependsOn(datamodel)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(
    mdocVariables := Map(
      "MODEL_ID" -> "1d99bab2-1470-46c8-aa00-a8a2ced5c60c",
      "PREDICTION_ID" -> "78d4345a-5c22-43ec-8a9a-fe354915c3eb",
      "WEBHOOK_ID" -> "f0ff558a-f989-4648-b606-abcf8b977e6c",
      "INVOCATION_TIME" -> "1583248611188"
    ),
    libraryDependencies ++= Seq(Dependencies.circeLiteral)
  )
