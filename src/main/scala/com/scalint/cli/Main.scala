package com.scalint.cli

import com.scalint.core._
import com.scalint.parser.ScalaParser
import com.scalint.reporter._
import com.scalint.rules.RuleRegistry
import scopt.OParser
import java.io.{File, PrintWriter}
import scala.io.Source

/**
 * CLI configuration
 */
case class CliConfig(
  paths: Seq[String] = Seq.empty,
  outputFormat: OutputFormat = OutputFormat.Text,
  outputFile: Option[String] = None,
  enabledRules: Set[String] = Set.empty,
  disabledRules: Set[String] = Set.empty,
  enabledCategories: Set[String] = Set.empty,
  minSeverity: String = "hint",
  dialect: String = "scala213",
  excludePatterns: Seq[String] = Seq.empty,
  noColors: Boolean = false,
  showSuggestions: Boolean = true,
  failOnWarning: Boolean = false,
  quiet: Boolean = false,
  verbose: Boolean = false,
  listRules: Boolean = false,
  showVersion: Boolean = false,
  configFile: Option[String] = None,
  fix: Boolean = false
)

/**
 * Main CLI entry point
 */
object Main {

  val appVersion = "1.0.0"

  def main(args: Array[String]): Unit = {
    val exitCode = run(args)
    sys.exit(exitCode)
  }

  def run(args: Array[String]): Int = {
    val builder = OParser.builder[CliConfig]
    import builder._

    val parser: OParser[_, CliConfig] = OParser.sequence(
      programName("scalint"),
      head("scalint", appVersion, "- Scala static analysis tool"),

      help('h', "help")
        .text("Show this help message"),

      version('v', "version")
        .text("Show version information"),

      opt[String]('f', "format")
        .valueName("<format>")
        .action((x, c) => c.copy(outputFormat = OutputFormat.fromString(x)))
        .text("Output format: text, json, compact, github, checkstyle (default: text)"),

      opt[String]('o', "output")
        .valueName("<file>")
        .action((x, c) => c.copy(outputFile = Some(x)))
        .text("Write output to file instead of stdout"),

      opt[Seq[String]]('e', "enable")
        .valueName("<rule1,rule2,...>")
        .action((x, c) => c.copy(enabledRules = x.toSet))
        .text("Enable only these rules (by ID or name)"),

      opt[Seq[String]]('d', "disable")
        .valueName("<rule1,rule2,...>")
        .action((x, c) => c.copy(disabledRules = x.toSet))
        .text("Disable these rules (by ID or name)"),

      opt[Seq[String]]('c', "category")
        .valueName("<cat1,cat2,...>")
        .action((x, c) => c.copy(enabledCategories = x.toSet))
        .text("Enable only these categories: style, bug, performance, security, complexity, functional, concurrency, resource, deprecation, type-safety"),

      opt[String]('s', "severity")
        .valueName("<level>")
        .action((x, c) => c.copy(minSeverity = x))
        .text("Minimum severity level: error, warning, info, hint (default: hint)"),

      opt[String]("dialect")
        .valueName("<dialect>")
        .action((x, c) => c.copy(dialect = x))
        .text("Scala dialect: scala213, scala212, scala3, sbt (default: scala213)"),

      opt[Seq[String]]('x', "exclude")
        .valueName("<pattern1,pattern2,...>")
        .action((x, c) => c.copy(excludePatterns = x))
        .text("Exclude files matching these glob patterns"),

      opt[Unit]("no-colors")
        .action((_, c) => c.copy(noColors = true))
        .text("Disable colored output"),

      opt[Unit]("no-suggestions")
        .action((_, c) => c.copy(showSuggestions = false))
        .text("Don't show fix suggestions"),

      opt[Unit]('w', "fail-on-warning")
        .action((_, c) => c.copy(failOnWarning = true))
        .text("Exit with error code if warnings are found"),

      opt[Unit]('q', "quiet")
        .action((_, c) => c.copy(quiet = true))
        .text("Only output issues, no summary"),

      opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("Show verbose output"),

      opt[Unit]('l', "list-rules")
        .action((_, c) => c.copy(listRules = true))
        .text("List all available rules"),

      opt[String]("config")
        .valueName("<file>")
        .action((x, c) => c.copy(configFile = Some(x)))
        .text("Load configuration from file"),

      arg[String]("<path>...")
        .unbounded()
        .optional()
        .action((x, c) => c.copy(paths = c.paths :+ x))
        .text("Files or directories to analyze")
    )

    OParser.parse(parser, args, CliConfig()) match {
      case Some(config) =>
        if (config.listRules) {
          printRules()
          0
        } else if (config.paths.isEmpty) {
          // Check for stdin
          if (System.in.available() > 0) {
            analyzeStdin(config)
          } else {
            println("Error: No input files specified. Use --help for usage.")
            println("Usage: scalint [options] <path>...")
            1
          }
        } else {
          analyze(config)
        }

      case None =>
        // Parser already printed error
        1
    }
  }

  private def analyze(config: CliConfig): Int = {
    val lintConfig = buildLintConfig(config)
    val analyzer = new Analyzer(lintConfig)
    val dialect = ScalaParser.ScalaDialect.fromString(config.dialect)

    if (config.verbose) {
      println(s"ScalaLint v$appVersion")
      println(s"Analyzing ${config.paths.size} path(s)...")
      println(s"Enabled rules: ${analyzer.enabledRuleInfo.size}")
      println()
    }

    val result = analyzer.analyze(config.paths, dialect)

    outputResult(result, config)

    // Determine exit code
    if (result.hasErrors) {
      1
    } else if (config.failOnWarning && result.warningCount > 0) {
      1
    } else {
      0
    }
  }

  private def analyzeStdin(config: CliConfig): Int = {
    val code = Source.stdin.mkString
    val lintConfig = buildLintConfig(config)
    val analyzer = new Analyzer(lintConfig)
    val dialect = ScalaParser.ScalaDialect.fromString(config.dialect)

    val result = analyzer.analyzeString(code, "<stdin>", dialect)
    val analysisResult = AnalysisResult(
      fileResults = Seq(result),
      totalFiles = 1,
      analyzedFiles = if (result.parseError.isEmpty) 1 else 0,
      skippedFiles = if (result.parseError.isDefined) 1 else 0
    )

    outputResult(analysisResult, config)

    if (result.hasErrors) 1
    else if (config.failOnWarning && result.hasWarnings) 1
    else 0
  }

  private def buildLintConfig(config: CliConfig): LintConfig = {
    val categories = if (config.enabledCategories.isEmpty) {
      Category.all.toSet
    } else {
      config.enabledCategories.flatMap(Category.fromString)
    }

    val minSeverity = Severity.fromString(config.minSeverity).getOrElse(Severity.Hint)

    LintConfig(
      enabledRules = config.enabledRules,
      disabledRules = config.disabledRules,
      enabledCategories = categories,
      minSeverity = minSeverity,
      excludePatterns = config.excludePatterns,
      failOnWarning = config.failOnWarning,
      configFile = config.configFile
    )
  }

  private def outputResult(result: AnalysisResult, config: CliConfig): Unit = {
    val useColors = !config.noColors && config.outputFile.isEmpty
    val reporter = Reporter.create(config.outputFormat, useColors, config.showSuggestions)

    val output = if (config.quiet && config.outputFormat == OutputFormat.Text) {
      result.fileResults.map(reporter.formatFile).mkString
    } else {
      reporter.format(result)
    }

    config.outputFile match {
      case Some(file) =>
        val pw = new PrintWriter(new File(file))
        try {
          pw.write(output)
        } finally {
          pw.close()
        }
        if (!config.quiet) {
          println(s"Output written to $file")
        }

      case None =>
        print(output)
    }
  }

  private def printRules(): Unit = {
    println(s"\nScalaLint v$appVersion - Available Rules\n")
    println(s"Total rules: ${RuleRegistry.ruleCount}\n")

    Category.all.foreach { category =>
      val rules = RuleRegistry.byCategory(category)
      if (rules.nonEmpty) {
        println(s"${category.name.toUpperCase} (${rules.size} rules)")
        println("=" * 50)
        println(s"${category.description}\n")

        rules.sortBy(_.id).foreach { rule =>
          val severityIcon = rule.severity match {
            case Severity.Error => "E"
            case Severity.Warning => "W"
            case Severity.Info => "I"
            case Severity.Hint => "H"
          }
          println(f"  ${rule.id}%-8s [$severityIcon] ${rule.name}%-30s")
          println(f"           ${rule.description}")
          println()
        }
        println()
      }
    }
  }
}
