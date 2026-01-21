// ScalaLint sbt Plugin
// Build configuration for the sbt plugin module

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-scalint",
    organization := "com.scalint",
    version := "0.1.0-SNAPSHOT",

    // sbt plugin settings
    sbtPlugin := true,

    // Scala version for sbt 1.x
    scalaVersion := "2.12.18",

    // Plugin dependencies
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.8.14",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),

    // Plugin scripted test framework
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,

    // Publishing settings
    publishMavenStyle := true,

    // Plugin description
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.9.7"
      }
    },

    // License
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),

    // Documentation
    Compile / doc / scalacOptions ++= Seq(
      "-doc-title", "ScalaLint sbt Plugin",
      "-doc-version", version.value
    )
  )
