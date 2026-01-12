package com.scalint.core

import com.scalint.parser.ScalaParser
import com.scalint.parser.ScalaParser._
import com.scalint.rules.RuleRegistry
import scala.meta._
import java.io.File
import scala.util.{Try, Success, Failure}

/**
 * Main analyzer that coordinates parsing and rule checking
 */
class Analyzer(config: LintConfig = LintConfig.default) {

  private val enabledRules: Seq[Rule] = RuleRegistry.enabledRules(config)

  /**
   * Analyze a single file
   */
  def analyzeFile(filePath: String, dialect: ScalaDialect = ScalaDialect.default): FileAnalysisResult = {
    ScalaParser.parseFile(filePath, dialect) match {
      case ParseSuccess(source, file) =>
        val issues = enabledRules.flatMap { rule =>
          Try(rule.check(source, file, config)) match {
            case Success(ruleIssues) => ruleIssues
            case Failure(e) =>
              // Log but don't fail on rule errors
              System.err.println(s"Warning: Rule ${rule.id} failed on $file: ${e.getMessage}")
              Seq.empty
          }
        }

        FileAnalysisResult(
          file = filePath,
          issues = issues.sortBy(i => (i.position.startLine, i.position.startColumn))
        )

      case ParseError(file, error, line) =>
        FileAnalysisResult(
          file = filePath,
          issues = Seq.empty,
          parseError = Some(s"Parse error${line.map(l => s" at line $l").getOrElse("")}: $error")
        )
    }
  }

  /**
   * Analyze source code from string
   */
  def analyzeString(
    code: String,
    fileName: String = "<input>",
    dialect: ScalaDialect = ScalaDialect.default
  ): FileAnalysisResult = {
    ScalaParser.parseString(code, fileName, dialect) match {
      case ParseSuccess(source, file) =>
        val issues = enabledRules.flatMap { rule =>
          Try(rule.check(source, file, config)) match {
            case Success(ruleIssues) => ruleIssues
            case Failure(e) =>
              System.err.println(s"Warning: Rule ${rule.id} failed: ${e.getMessage}")
              Seq.empty
          }
        }

        FileAnalysisResult(
          file = fileName,
          issues = issues.sortBy(i => (i.position.startLine, i.position.startColumn))
        )

      case ParseError(file, error, line) =>
        FileAnalysisResult(
          file = fileName,
          issues = Seq.empty,
          parseError = Some(s"Parse error${line.map(l => s" at line $l").getOrElse("")}: $error")
        )
    }
  }

  /**
   * Analyze multiple files
   */
  def analyzeFiles(files: Seq[String], dialect: ScalaDialect = ScalaDialect.default): AnalysisResult = {
    val results = files.map(f => analyzeFile(f, dialect))

    AnalysisResult(
      fileResults = results,
      totalFiles = files.size,
      analyzedFiles = results.count(_.parseError.isEmpty),
      skippedFiles = results.count(_.parseError.isDefined)
    )
  }

  /**
   * Analyze a directory recursively
   */
  def analyzeDirectory(
    directory: String,
    dialect: ScalaDialect = ScalaDialect.default
  ): AnalysisResult = {
    val files = ScalaParser.findScalaFiles(
      directory,
      config.includePatterns,
      config.excludePatterns
    )

    analyzeFiles(files, dialect)
  }

  /**
   * Analyze paths (files or directories)
   */
  def analyze(
    paths: Seq[String],
    dialect: ScalaDialect = ScalaDialect.default
  ): AnalysisResult = {
    val allFiles = paths.flatMap { path =>
      val file = new File(path)
      if (file.isDirectory) {
        ScalaParser.findScalaFiles(path, config.includePatterns, config.excludePatterns)
      } else if (file.isFile && (path.endsWith(".scala") || path.endsWith(".sc"))) {
        Seq(path)
      } else {
        System.err.println(s"Warning: Skipping '$path' (not a Scala file or directory)")
        Seq.empty
      }
    }.distinct

    analyzeFiles(allFiles, dialect)
  }

  /**
   * Get information about enabled rules
   */
  def enabledRuleInfo: Seq[(String, String, Category, Severity)] = {
    enabledRules.map(r => (r.id, r.name, r.category, r.severity))
  }
}

object Analyzer {

  /**
   * Create analyzer with default configuration
   */
  def default: Analyzer = new Analyzer(LintConfig.default)

  /**
   * Create analyzer with custom configuration
   */
  def withConfig(config: LintConfig): Analyzer = new Analyzer(config)

  /**
   * Quick analysis of a single file with default config
   */
  def quickAnalyze(filePath: String): FileAnalysisResult = {
    default.analyzeFile(filePath)
  }

  /**
   * Quick analysis of code string with default config
   */
  def quickAnalyzeString(code: String): FileAnalysisResult = {
    default.analyzeString(code)
  }
}
