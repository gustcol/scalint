package com.scalint.core

import scala.meta._

/**
 * Severity levels for lint issues
 */
sealed trait Severity extends Ordered[Severity] {
  def level: Int
  def name: String
  def compare(that: Severity): Int = this.level - that.level
}

object Severity {
  case object Error extends Severity {
    val level = 3
    val name = "error"
  }
  case object Warning extends Severity {
    val level = 2
    val name = "warning"
  }
  case object Info extends Severity {
    val level = 1
    val name = "info"
  }
  case object Hint extends Severity {
    val level = 0
    val name = "hint"
  }

  def fromString(s: String): Option[Severity] = s.toLowerCase match {
    case "error" => Some(Error)
    case "warning" => Some(Warning)
    case "info" => Some(Info)
    case "hint" => Some(Hint)
    case _ => None
  }

  val all: Seq[Severity] = Seq(Error, Warning, Info, Hint)
}

/**
 * Categories of lint rules
 */
sealed trait Category {
  def name: String
  def description: String
}

object Category {
  case object Style extends Category {
    val name = "style"
    val description = "Code style and naming conventions"
  }
  case object Bug extends Category {
    val name = "bug"
    val description = "Potential bugs and logical errors"
  }
  case object Performance extends Category {
    val name = "performance"
    val description = "Performance issues and inefficiencies"
  }
  case object Security extends Category {
    val name = "security"
    val description = "Security vulnerabilities"
  }
  case object Complexity extends Category {
    val name = "complexity"
    val description = "Code complexity issues"
  }
  case object FunctionalStyle extends Category {
    val name = "functional"
    val description = "Functional programming best practices"
  }
  case object Concurrency extends Category {
    val name = "concurrency"
    val description = "Concurrency and thread safety issues"
  }
  case object Resource extends Category {
    val name = "resource"
    val description = "Resource management issues"
  }
  case object Deprecation extends Category {
    val name = "deprecation"
    val description = "Usage of deprecated features"
  }
  case object TypeSafety extends Category {
    val name = "type-safety"
    val description = "Type safety issues"
  }
  case object Spark extends Category {
    val name = "spark"
    val description = "Apache Spark best practices and anti-patterns"
  }
  case object Test extends Category {
    val name = "test"
    val description = "Test code quality and isolation"
  }
  case object Scala3 extends Category {
    val name = "scala3"
    val description = "Scala 3 specific patterns and migrations"
  }

  val all: Seq[Category] = Seq(
    Style, Bug, Performance, Security, Complexity,
    FunctionalStyle, Concurrency, Resource, Deprecation, TypeSafety,
    Spark, Test, Scala3
  )

  def fromString(s: String): Option[Category] = all.find(_.name == s.toLowerCase)
}

/**
 * Position in source code
 */
case class SourcePosition(
  file: String,
  startLine: Int,
  startColumn: Int,
  endLine: Int,
  endColumn: Int
) {
  def pretty: String = s"$file:$startLine:$startColumn"
}

object SourcePosition {
  def fromMeta(pos: Position, file: String): SourcePosition = {
    SourcePosition(
      file = file,
      startLine = pos.startLine + 1,
      startColumn = pos.startColumn + 1,
      endLine = pos.endLine + 1,
      endColumn = pos.endColumn + 1
    )
  }

  val empty: SourcePosition = SourcePosition("", 0, 0, 0, 0)
}

/**
 * A lint issue found in the code
 */
case class LintIssue(
  ruleId: String,
  ruleName: String,
  category: Category,
  severity: Severity,
  message: String,
  position: SourcePosition,
  suggestion: Option[String] = None,
  codeSnippet: Option[String] = None,
  explanation: Option[String] = None
)

/**
 * Result of analyzing a single file
 */
case class FileAnalysisResult(
  file: String,
  issues: Seq[LintIssue],
  parseError: Option[String] = None
) {
  def hasErrors: Boolean = issues.exists(_.severity == Severity.Error) || parseError.isDefined
  def hasWarnings: Boolean = issues.exists(_.severity == Severity.Warning)
  def issueCount: Int = issues.size
}

/**
 * Result of analyzing multiple files
 */
case class AnalysisResult(
  fileResults: Seq[FileAnalysisResult],
  totalFiles: Int,
  analyzedFiles: Int,
  skippedFiles: Int
) {
  def allIssues: Seq[LintIssue] = fileResults.flatMap(_.issues)
  def errorCount: Int = allIssues.count(_.severity == Severity.Error)
  def warningCount: Int = allIssues.count(_.severity == Severity.Warning)
  def infoCount: Int = allIssues.count(_.severity == Severity.Info)
  def hintCount: Int = allIssues.count(_.severity == Severity.Hint)
  def totalIssueCount: Int = allIssues.size
  def hasErrors: Boolean = fileResults.exists(_.hasErrors)
}

/**
 * Configuration for the linter
 */
case class LintConfig(
  enabledRules: Set[String] = Set.empty,
  disabledRules: Set[String] = Set.empty,
  enabledCategories: Set[Category] = Category.all.toSet,
  minSeverity: Severity = Severity.Hint,
  excludePatterns: Seq[String] = Seq.empty,
  includePatterns: Seq[String] = Seq("**/*.scala", "**/*.sc"),
  maxIssues: Int = Int.MaxValue,
  failOnWarning: Boolean = false,
  configFile: Option[String] = None
) {
  def isRuleEnabled(ruleId: String): Boolean = {
    if (disabledRules.contains(ruleId)) false
    else if (enabledRules.isEmpty) true
    else enabledRules.contains(ruleId)
  }
}

object LintConfig {
  val default: LintConfig = LintConfig()
}
