import sbtassembly.AssemblyPlugin.autoImport._

lazy val root = (project in file("."))
  .settings(
    name := "scalint",
    version := "1.0.0",
    scalaVersion := "2.13.12",
    organization := "com.scalint",

    libraryDependencies ++= Seq(
      // Scala Meta for parsing Scala code
      "org.scalameta" %% "scalameta" % "4.8.14",
      "org.scalameta" %% "semanticdb-scalac" % "4.8.14" cross CrossVersion.full,

      // CLI parsing
      "com.github.scopt" %% "scopt" % "4.1.0",

      // JSON output
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",

      // Console colors
      "com.lihaoyi" %% "fansi" % "0.4.0",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalameta" %% "munit" % "0.7.29" % Test
    ),

    // Assembly settings for creating fat JAR
    assembly / mainClass := Some("com.scalint.cli.Main"),
    assembly / assemblyJarName := "scalint.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case x => MergeStrategy.first
    },

    // Compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint:_",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )
  )
