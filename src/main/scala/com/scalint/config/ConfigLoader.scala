package com.scalint.config

import com.scalint.core._
import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.io.Source
import scala.util.{Try, Using}

/**
 * Configuration file loader for .scalint.yaml
 *
 * Supports hierarchical configuration:
 *  1. User global: ~/.scalint.yaml
 *  2. Project root: .scalint.yaml
 *  3. Command line arguments (highest priority)
 *
 * See README.md for example .scalint.yaml configuration format.
 */
object ConfigLoader {

  val ConfigFileName = ".scalint.yaml"
  val BaselineFileName = ".scalint-baseline.json"
  val GlobalConfigPath: Path = Paths.get(System.getProperty("user.home"), ConfigFileName)

  /**
   * Load configuration from files and merge with defaults
   */
  def load(projectPath: String = "."): LintConfig = {
    val projectDir = Paths.get(projectPath).toAbsolutePath
    val projectConfig = findConfigFile(projectDir)
    val globalConfig = if (Files.exists(GlobalConfigPath)) Some(GlobalConfigPath) else None

    // Start with defaults
    var config = LintConfig.default

    // Apply global config
    globalConfig.foreach { path =>
      parseConfigFile(path).foreach { parsed =>
        config = mergeConfig(config, parsed)
      }
    }

    // Apply project config (overrides global)
    projectConfig.foreach { path =>
      parseConfigFile(path).foreach { parsed =>
        config = mergeConfig(config, parsed)
      }
    }

    config.copy(configFile = projectConfig.map(_.toString).orElse(globalConfig.map(_.toString)))
  }

  /**
   * Find configuration file in project directory or parent directories
   */
  def findConfigFile(startDir: Path): Option[Path] = {
    var current = startDir.toAbsolutePath
    while (current != null) {
      val configPath = current.resolve(ConfigFileName)
      if (Files.exists(configPath)) {
        return Some(configPath)
      }
      current = current.getParent
    }
    None
  }

  /**
   * Parse a YAML configuration file (simplified parser)
   */
  def parseConfigFile(path: Path): Option[LintConfig] = {
    Try {
      Using.resource(Source.fromFile(path.toFile)) { source =>
        val content = source.mkString
        parseYaml(content)
      }
    }.toOption.flatten
  }

  /**
   * Simple YAML parser for our configuration format
   * Note: For production, use a proper YAML library like SnakeYAML
   */
  private def parseYaml(content: String): Option[LintConfig] = {
    val lines = content.split("\n").map(_.trim).filterNot(l => l.isEmpty || l.startsWith("#"))

    var enabledRules = Set.empty[String]
    var disabledRules = Set.empty[String]
    var enabledCategories = Category.all.toSet
    var minSeverity: Severity = Severity.Hint
    var excludePatterns = Seq.empty[String]
    var includePatterns = Seq("**/*.scala", "**/*.sc")
    var maxIssues = Int.MaxValue
    var failOnWarning = false

    var currentSection = ""
    var currentSubSection = ""

    lines.foreach { line =>
      if (line.endsWith(":") && !line.contains(" ")) {
        currentSection = line.dropRight(1)
        currentSubSection = ""
      } else if (line.startsWith("-") && currentSection.nonEmpty) {
        val value = line.drop(1).trim.stripPrefix("\"").stripSuffix("\"")
        (currentSection, currentSubSection) match {
          case ("rules", "enabled") => enabledRules += value
          case ("rules", "disabled") => disabledRules += value
          case ("categories", "enabled") =>
            Category.fromString(value).foreach { cat =>
              enabledCategories = enabledCategories + cat
            }
          case ("categories", "disabled") =>
            Category.fromString(value).foreach { cat =>
              enabledCategories = enabledCategories - cat
            }
          case ("include", _) => includePatterns :+= value
          case ("exclude", _) => excludePatterns :+= value
          case _ =>
        }
      } else if (line.contains(":") && currentSection.nonEmpty) {
        val parts = line.split(":", 2).map(_.trim)
        if (parts.length == 2) {
          val key = parts(0)
          val value = parts(1).stripPrefix("\"").stripSuffix("\"")

          if (value.isEmpty) {
            currentSubSection = key
          } else {
            key match {
              case "minSeverity" =>
                Severity.fromString(value).foreach(minSeverity = _)
              case "maxIssues" =>
                Try(value.toInt).foreach(maxIssues = _)
              case "failOnWarning" =>
                failOnWarning = value.toLowerCase == "true"
              case _ =>
            }
          }
        }
      } else if (line.contains(":") && currentSection.isEmpty) {
        val parts = line.split(":", 2).map(_.trim)
        if (parts.length == 2) {
          val key = parts(0)
          val value = parts(1).stripPrefix("\"").stripSuffix("\"")
          key match {
            case "minSeverity" =>
              Severity.fromString(value).foreach(minSeverity = _)
            case "maxIssues" =>
              Try(value.toInt).foreach(maxIssues = _)
            case "failOnWarning" =>
              failOnWarning = value.toLowerCase == "true"
            case _ =>
              currentSection = key
          }
        }
      }
    }

    Some(LintConfig(
      enabledRules = enabledRules,
      disabledRules = disabledRules,
      enabledCategories = enabledCategories,
      minSeverity = minSeverity,
      excludePatterns = excludePatterns,
      includePatterns = includePatterns,
      maxIssues = maxIssues,
      failOnWarning = failOnWarning
    ))
  }

  /**
   * Merge two configurations (second takes precedence)
   */
  private def mergeConfig(base: LintConfig, override_ : LintConfig): LintConfig = {
    LintConfig(
      enabledRules = if (override_.enabledRules.nonEmpty) override_.enabledRules else base.enabledRules,
      disabledRules = base.disabledRules ++ override_.disabledRules,
      enabledCategories = override_.enabledCategories,
      minSeverity = override_.minSeverity,
      excludePatterns = base.excludePatterns ++ override_.excludePatterns,
      includePatterns = if (override_.includePatterns.nonEmpty &&
        override_.includePatterns != Seq("**/*.scala", "**/*.sc"))
        override_.includePatterns else base.includePatterns,
      maxIssues = if (override_.maxIssues != Int.MaxValue) override_.maxIssues else base.maxIssues,
      failOnWarning = override_.failOnWarning || base.failOnWarning,
      configFile = override_.configFile.orElse(base.configFile)
    )
  }

  /**
   * Generate a sample configuration file
   */
  def generateSampleConfig(): String = {
    """# ScalaLint Configuration
      |# https://github.com/your-repo/scalint
      |
      |# Enable specific rules only (leave empty to enable all)
      |rules:
      |  enabled: []
      |  disabled:
      |    - CX009  # magic-number (noisy for some projects)
      |    - S001   # line-length
      |
      |# Categories to enable/disable
      |categories:
      |  enabled:
      |    - bug
      |    - security
      |    - performance
      |    - spark
      |    - delta
      |    - concurrency
      |  disabled:
      |    - style
      |
      |# Minimum severity level: error, warning, info, hint
      |minSeverity: info
      |
      |# Files to analyze
      |include:
      |  - "src/main/**/*.scala"
      |  - "src/test/**/*.scala"
      |
      |# Files to exclude
      |exclude:
      |  - "**/target/**"
      |  - "**/generated/**"
      |  - "**/*.g.scala"
      |
      |# Maximum issues to report (useful for gradual adoption)
      |maxIssues: 200
      |
      |# Fail CI on warnings (not just errors)
      |failOnWarning: false
      |
      |# Baseline file for ignoring existing issues
      |# baseline: .scalint-baseline.json
      |""".stripMargin
  }

  /**
   * Write sample configuration to project root
   */
  def initConfig(projectPath: String = "."): Boolean = {
    val configPath = Paths.get(projectPath, ConfigFileName)
    if (Files.exists(configPath)) {
      false // Already exists
    } else {
      Try {
        Files.write(configPath, generateSampleConfig().getBytes("UTF-8"))
        true
      }.getOrElse(false)
    }
  }
}

/**
 * Rule-specific configuration
 * Allows customizing thresholds for individual rules
 */
case class RuleSpecificConfig(
  ruleId: String,
  settings: Map[String, Any]
) {
  def getInt(key: String, default: Int): Int =
    settings.get(key).collect { case i: Int => i }.getOrElse(default)

  def getString(key: String, default: String): String =
    settings.get(key).collect { case s: String => s }.getOrElse(default)

  def getBoolean(key: String, default: Boolean): Boolean =
    settings.get(key).collect { case b: Boolean => b }.getOrElse(default)
}
