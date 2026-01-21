package com.scalint.sbt

import sbt._
import sbt.Keys._

/**
 * ScalaLint sbt Plugin
 *
 * Integrates ScalaLint static analysis into sbt builds.
 *
 * Usage:
 *   // In project/plugins.sbt
 *   addSbtPlugin("com.scalint" % "sbt-scalint" % "0.1.0")
 *
 *   // In build.sbt
 *   enablePlugins(ScalintPlugin)
 *
 * Tasks:
 *   scalint              - Run linting on main sources
 *   scalintTest          - Run linting on test sources
 *   scalintAll           - Run linting on all sources
 *   scalintFix           - Apply auto-fixes
 *   scalintGenerateBaseline - Generate baseline for legacy code
 *
 * Settings:
 *   scalintConfig        - Path to .scalint.yaml config
 *   scalintFailOnError   - Fail build on errors (default: true)
 *   scalintFailOnWarning - Fail build on warnings (default: false)
 *   scalintExclude       - File patterns to exclude
 *   scalintRules         - Specific rules to enable
 *   scalintDisabledRules - Rules to disable
 *   scalintReportFormat  - Report format (console, json, html)
 */
object ScalintPlugin extends AutoPlugin {

  override def trigger = noTrigger
  override def requires = plugins.JvmPlugin

  object autoImport {
    // Tasks
    val scalint = taskKey[Unit]("Run ScalaLint on main sources")
    val scalintTest = taskKey[Unit]("Run ScalaLint on test sources")
    val scalintAll = taskKey[Unit]("Run ScalaLint on all sources")
    val scalintFix = taskKey[Unit]("Apply ScalaLint auto-fixes")
    val scalintGenerateBaseline = taskKey[Unit]("Generate baseline file")
    val scalintCleanBaseline = taskKey[Unit]("Clean stale baseline entries")
    val scalintWatch = taskKey[Unit]("Run ScalaLint in watch mode")

    // Settings
    val scalintConfig = settingKey[Option[File]]("Path to .scalint.yaml configuration file")
    val scalintFailOnError = settingKey[Boolean]("Fail the build if errors are found")
    val scalintFailOnWarning = settingKey[Boolean]("Fail the build if warnings are found")
    val scalintExclude = settingKey[Seq[String]]("File patterns to exclude from linting")
    val scalintInclude = settingKey[Seq[String]]("File patterns to include in linting")
    val scalintRules = settingKey[Seq[String]]("Specific rules to enable (empty = all)")
    val scalintDisabledRules = settingKey[Seq[String]]("Rules to disable")
    val scalintCategories = settingKey[Seq[String]]("Categories to enable (empty = all)")
    val scalintReportFormat = settingKey[String]("Report format: console, json, html, sarif")
    val scalintReportFile = settingKey[Option[File]]("File to write report to")
    val scalintBaseline = settingKey[Option[File]]("Path to baseline file")
    val scalintMaxIssues = settingKey[Int]("Maximum issues before stopping (0 = unlimited)")
    val scalintVerbose = settingKey[Boolean]("Enable verbose output")

    // Result type
    case class ScalintResult(
      totalFiles: Int,
      analyzedFiles: Int,
      errorCount: Int,
      warningCount: Int,
      infoCount: Int,
      hintCount: Int
    ) {
      def hasErrors: Boolean = errorCount > 0
      def hasWarnings: Boolean = warningCount > 0
      def totalIssues: Int = errorCount + warningCount + infoCount + hintCount
    }
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Default settings
    scalintConfig := None,
    scalintFailOnError := true,
    scalintFailOnWarning := false,
    scalintExclude := Seq.empty,
    scalintInclude := Seq("**/*.scala"),
    scalintRules := Seq.empty,
    scalintDisabledRules := Seq.empty,
    scalintCategories := Seq.empty,
    scalintReportFormat := "console",
    scalintReportFile := None,
    scalintBaseline := None,
    scalintMaxIssues := 0,
    scalintVerbose := false,

    // Main source linting task
    scalint := {
      val log = streams.value.log
      val sources = (Compile / unmanagedSourceDirectories).value
      val config = buildConfig(
        scalintConfig.value,
        scalintRules.value,
        scalintDisabledRules.value,
        scalintCategories.value,
        scalintExclude.value,
        scalintInclude.value
      )

      log.info("Running ScalaLint on main sources...")

      val result = runLinting(
        sources = sources,
        config = config,
        reportFormat = scalintReportFormat.value,
        reportFile = scalintReportFile.value,
        baseline = scalintBaseline.value,
        maxIssues = scalintMaxIssues.value,
        verbose = scalintVerbose.value,
        log = log
      )

      handleResult(result, scalintFailOnError.value, scalintFailOnWarning.value, log)
    },

    // Test source linting task
    scalintTest := {
      val log = streams.value.log
      val sources = (Test / unmanagedSourceDirectories).value
      val config = buildConfig(
        scalintConfig.value,
        scalintRules.value,
        scalintDisabledRules.value,
        scalintCategories.value,
        scalintExclude.value,
        scalintInclude.value
      )

      log.info("Running ScalaLint on test sources...")

      val result = runLinting(
        sources = sources,
        config = config,
        reportFormat = scalintReportFormat.value,
        reportFile = scalintReportFile.value,
        baseline = scalintBaseline.value,
        maxIssues = scalintMaxIssues.value,
        verbose = scalintVerbose.value,
        log = log
      )

      handleResult(result, scalintFailOnError.value, scalintFailOnWarning.value, log)
    },

    // All sources linting task
    scalintAll := {
      val log = streams.value.log
      val mainSources = (Compile / unmanagedSourceDirectories).value
      val testSources = (Test / unmanagedSourceDirectories).value
      val config = buildConfig(
        scalintConfig.value,
        scalintRules.value,
        scalintDisabledRules.value,
        scalintCategories.value,
        scalintExclude.value,
        scalintInclude.value
      )

      log.info("Running ScalaLint on all sources...")

      val result = runLinting(
        sources = mainSources ++ testSources,
        config = config,
        reportFormat = scalintReportFormat.value,
        reportFile = scalintReportFile.value,
        baseline = scalintBaseline.value,
        maxIssues = scalintMaxIssues.value,
        verbose = scalintVerbose.value,
        log = log
      )

      handleResult(result, scalintFailOnError.value, scalintFailOnWarning.value, log)
    },

    // Auto-fix task
    scalintFix := {
      val log = streams.value.log
      val sources = (Compile / unmanagedSourceDirectories).value
      val config = buildConfig(
        scalintConfig.value,
        scalintRules.value,
        scalintDisabledRules.value,
        scalintCategories.value,
        scalintExclude.value,
        scalintInclude.value
      )

      log.info("Running ScalaLint auto-fix...")
      runAutoFix(sources, config, log)
    },

    // Generate baseline task
    scalintGenerateBaseline := {
      val log = streams.value.log
      val sources = (Compile / unmanagedSourceDirectories).value ++ (Test / unmanagedSourceDirectories).value
      val baselineFile = scalintBaseline.value.getOrElse(baseDirectory.value / ".scalint-baseline.json")
      val config = buildConfig(
        scalintConfig.value,
        scalintRules.value,
        scalintDisabledRules.value,
        scalintCategories.value,
        scalintExclude.value,
        scalintInclude.value
      )

      log.info(s"Generating baseline at ${baselineFile.getPath}...")
      generateBaseline(sources, config, baselineFile, log)
    },

    // Clean baseline task
    scalintCleanBaseline := {
      val log = streams.value.log
      val sources = (Compile / unmanagedSourceDirectories).value ++ (Test / unmanagedSourceDirectories).value
      val baselineFile = scalintBaseline.value.getOrElse(baseDirectory.value / ".scalint-baseline.json")
      val config = buildConfig(
        scalintConfig.value,
        scalintRules.value,
        scalintDisabledRules.value,
        scalintCategories.value,
        scalintExclude.value,
        scalintInclude.value
      )

      log.info(s"Cleaning baseline at ${baselineFile.getPath}...")
      cleanBaseline(sources, config, baselineFile, log)
    },

    // Watch mode task
    scalintWatch := {
      val log = streams.value.log
      val sources = (Compile / unmanagedSourceDirectories).value
      val config = buildConfig(
        scalintConfig.value,
        scalintRules.value,
        scalintDisabledRules.value,
        scalintCategories.value,
        scalintExclude.value,
        scalintInclude.value
      )

      log.info("Starting ScalaLint watch mode...")
      log.info("Press Ctrl+C to exit")
      runWatchMode(sources, config, log)
    }
  )

  // Build configuration from settings
  private def buildConfig(
    configFile: Option[File],
    rules: Seq[String],
    disabledRules: Seq[String],
    categories: Seq[String],
    exclude: Seq[String],
    include: Seq[String]
  ): ScalintConfig = {
    ScalintConfig(
      configFile = configFile,
      enabledRules = rules,
      disabledRules = disabledRules,
      enabledCategories = categories,
      excludePatterns = exclude,
      includePatterns = include
    )
  }

  // Run linting on sources
  private def runLinting(
    sources: Seq[File],
    config: ScalintConfig,
    reportFormat: String,
    reportFile: Option[File],
    baseline: Option[File],
    maxIssues: Int,
    verbose: Boolean,
    log: Logger
  ): ScalintResult = {
    val files = findScalaFiles(sources, config.includePatterns, config.excludePatterns)

    if (files.isEmpty) {
      log.warn("No Scala files found to lint")
      return ScalintResult(0, 0, 0, 0, 0, 0)
    }

    if (verbose) {
      log.info(s"Found ${files.size} Scala files to analyze")
    }

    // This would call the actual ScalaLint engine
    // For now, we'll create a placeholder implementation
    val result = ScalintEngine.analyze(files, config, baseline)

    // Report results
    reportFormat match {
      case "console" => printConsoleReport(result, log)
      case "json" => writeJsonReport(result, reportFile, log)
      case "html" => writeHtmlReport(result, reportFile, log)
      case "sarif" => writeSarifReport(result, reportFile, log)
      case _ => printConsoleReport(result, log)
    }

    result
  }

  // Run auto-fix
  private def runAutoFix(sources: Seq[File], config: ScalintConfig, log: Logger): Unit = {
    val files = findScalaFiles(sources, config.includePatterns, config.excludePatterns)
    val fixed = ScalintEngine.fix(files, config)
    log.info(s"Applied $fixed auto-fixes")
  }

  // Generate baseline
  private def generateBaseline(
    sources: Seq[File],
    config: ScalintConfig,
    baselineFile: File,
    log: Logger
  ): Unit = {
    val files = findScalaFiles(sources, config.includePatterns, config.excludePatterns)
    val count = ScalintEngine.generateBaseline(files, config, baselineFile)
    log.info(s"Generated baseline with $count entries")
  }

  // Clean baseline
  private def cleanBaseline(
    sources: Seq[File],
    config: ScalintConfig,
    baselineFile: File,
    log: Logger
  ): Unit = {
    val files = findScalaFiles(sources, config.includePatterns, config.excludePatterns)
    val (removed, remaining) = ScalintEngine.cleanBaseline(files, config, baselineFile)
    log.info(s"Removed $removed stale entries, $remaining remaining")
  }

  // Run watch mode
  private def runWatchMode(sources: Seq[File], config: ScalintConfig, log: Logger): Unit = {
    ScalintEngine.watch(sources, config)
  }

  // Find Scala files matching patterns
  private def findScalaFiles(
    sources: Seq[File],
    include: Seq[String],
    exclude: Seq[String]
  ): Seq[File] = {
    sources.flatMap { dir =>
      if (dir.exists && dir.isDirectory) {
        findScalaFilesRecursive(dir, include, exclude)
      } else {
        Seq.empty
      }
    }
  }

  private def findScalaFilesRecursive(
    dir: File,
    include: Seq[String],
    exclude: Seq[String]
  ): Seq[File] = {
    val skipDirs = Set("target", "node_modules", ".git", ".idea", ".bsp")

    if (skipDirs.contains(dir.getName)) {
      Seq.empty
    } else {
      val files = Option(dir.listFiles()).getOrElse(Array.empty)
      files.flatMap { f =>
        if (f.isDirectory) {
          findScalaFilesRecursive(f, include, exclude)
        } else if (f.getName.endsWith(".scala") || f.getName.endsWith(".sc")) {
          if (matchesPatterns(f, include, exclude)) Seq(f) else Seq.empty
        } else {
          Seq.empty
        }
      }.toSeq
    }
  }

  private def matchesPatterns(file: File, include: Seq[String], exclude: Seq[String]): Boolean = {
    // Simplified pattern matching - would use proper glob matching in production
    val path = file.getAbsolutePath
    val included = include.isEmpty || include.exists(p => matchGlob(path, p))
    val excluded = exclude.nonEmpty && exclude.exists(p => matchGlob(path, p))
    included && !excluded
  }

  private def matchGlob(path: String, pattern: String): Boolean = {
    val regex = pattern
      .replace(".", "\\.")
      .replace("**", ".*")
      .replace("*", "[^/]*")
    path.matches(regex)
  }

  // Handle result - fail build if needed
  private def handleResult(
    result: ScalintResult,
    failOnError: Boolean,
    failOnWarning: Boolean,
    log: Logger
  ): Unit = {
    if (failOnError && result.hasErrors) {
      throw new MessageOnlyException(s"ScalaLint found ${result.errorCount} error(s)")
    }
    if (failOnWarning && result.hasWarnings) {
      throw new MessageOnlyException(s"ScalaLint found ${result.warningCount} warning(s)")
    }

    if (result.totalIssues == 0) {
      log.success("No issues found!")
    } else {
      log.info(s"Found ${result.totalIssues} issue(s)")
    }
  }

  // Report printing
  private def printConsoleReport(result: ScalintResult, log: Logger): Unit = {
    log.info(s"Analyzed ${result.analyzedFiles} of ${result.totalFiles} files")
    if (result.errorCount > 0) log.error(s"  Errors: ${result.errorCount}")
    if (result.warningCount > 0) log.warn(s"  Warnings: ${result.warningCount}")
    if (result.infoCount > 0) log.info(s"  Info: ${result.infoCount}")
    if (result.hintCount > 0) log.info(s"  Hints: ${result.hintCount}")
  }

  private def writeJsonReport(result: ScalintResult, file: Option[File], log: Logger): Unit = {
    file.foreach { f =>
      log.info(s"Writing JSON report to ${f.getPath}")
      // Would write actual JSON report
    }
  }

  private def writeHtmlReport(result: ScalintResult, file: Option[File], log: Logger): Unit = {
    file.foreach { f =>
      log.info(s"Writing HTML report to ${f.getPath}")
      // Would write actual HTML report
    }
  }

  private def writeSarifReport(result: ScalintResult, file: Option[File], log: Logger): Unit = {
    file.foreach { f =>
      log.info(s"Writing SARIF report to ${f.getPath}")
      // Would write actual SARIF report
    }
  }
}

// Configuration case class
case class ScalintConfig(
  configFile: Option[File] = None,
  enabledRules: Seq[String] = Seq.empty,
  disabledRules: Seq[String] = Seq.empty,
  enabledCategories: Seq[String] = Seq.empty,
  excludePatterns: Seq[String] = Seq.empty,
  includePatterns: Seq[String] = Seq("**/*.scala")
)

// Placeholder for the actual ScalaLint engine integration
object ScalintEngine {
  import ScalintPlugin.autoImport.ScalintResult

  def analyze(
    files: Seq[File],
    config: ScalintConfig,
    baseline: Option[File]
  ): ScalintResult = {
    // This would integrate with the actual ScalaLint core
    // For now, return a placeholder
    ScalintResult(
      totalFiles = files.size,
      analyzedFiles = files.size,
      errorCount = 0,
      warningCount = 0,
      infoCount = 0,
      hintCount = 0
    )
  }

  def fix(files: Seq[File], config: ScalintConfig): Int = {
    // Would apply auto-fixes
    0
  }

  def generateBaseline(files: Seq[File], config: ScalintConfig, output: File): Int = {
    // Would generate baseline
    0
  }

  def cleanBaseline(files: Seq[File], config: ScalintConfig, baseline: File): (Int, Int) = {
    // Would clean baseline, returns (removed, remaining)
    (0, 0)
  }

  def watch(sources: Seq[File], config: ScalintConfig): Unit = {
    // Would run watch mode
  }
}
