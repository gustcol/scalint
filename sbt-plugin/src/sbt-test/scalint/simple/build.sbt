// Simple test project for ScalaLint sbt plugin

lazy val root = (project in file("."))
  .enablePlugins(ScalintPlugin)
  .settings(
    name := "scalint-test",
    version := "1.0",
    scalaVersion := "2.13.12",

    // ScalaLint settings
    scalintFailOnError := false,
    scalintFailOnWarning := false,
    scalintVerbose := true
  )
