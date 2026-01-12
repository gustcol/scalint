package com.scalint.reporter

import com.scalint.core._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import fansi.{Bold, Color, Str}

/**
 * Output format for reports
 */
sealed trait OutputFormat {
  def name: String
}

object OutputFormat {
  case object Text extends OutputFormat { val name = "text" }
  case object Json extends OutputFormat { val name = "json" }
  case object Compact extends OutputFormat { val name = "compact" }
  case object GitHub extends OutputFormat { val name = "github" }
  case object Checkstyle extends OutputFormat { val name = "checkstyle" }

  def fromString(s: String): OutputFormat = s.toLowerCase match {
    case "json" => Json
    case "compact" => Compact
    case "github" => GitHub
    case "checkstyle" => Checkstyle
    case _ => Text
  }
}

/**
 * Reporter for formatting analysis results
 */
trait Reporter {
  def format(result: AnalysisResult): String
  def formatFile(result: FileAnalysisResult): String
}

/**
 * Text reporter with colored output
 */
class TextReporter(useColors: Boolean = true, showSuggestions: Boolean = true) extends Reporter {

  private def color(str: String, c: fansi.Attr): String = {
    if (useColors) c(str).render else str
  }

  private def severityColor(severity: Severity): fansi.Attr = severity match {
    case Severity.Error => Color.Red
    case Severity.Warning => Color.Yellow
    case Severity.Info => Color.Blue
    case Severity.Hint => Color.Cyan
  }

  private def formatSeverity(severity: Severity): String = {
    val icon = severity match {
      case Severity.Error => "E"
      case Severity.Warning => "W"
      case Severity.Info => "I"
      case Severity.Hint => "H"
    }
    color(s"[$icon]", severityColor(severity))
  }

  def formatFile(result: FileAnalysisResult): String = {
    val sb = new StringBuilder

    if (result.parseError.isDefined) {
      sb.append(color(s"\n${result.file}\n", Bold.On))
      sb.append(color(s"  Parse error: ${result.parseError.get}\n", Color.Red))
      return sb.toString
    }

    if (result.issues.isEmpty) {
      return ""
    }

    sb.append(color(s"\n${result.file}\n", Bold.On))

    result.issues.foreach { issue =>
      val pos = s"${issue.position.startLine}:${issue.position.startColumn}"
      val severity = formatSeverity(issue.severity)
      val rule = color(s"[${issue.ruleId}]", Color.Magenta)

      sb.append(s"  $pos  $severity $rule ${issue.message}\n")

      if (showSuggestions && issue.suggestion.isDefined) {
        sb.append(color(s"        Suggestion: ${issue.suggestion.get}\n", Color.Green))
      }
    }

    sb.toString
  }

  def format(result: AnalysisResult): String = {
    val sb = new StringBuilder

    sb.append(color("\n=== ScalaLint Analysis Report ===\n", Bold.On))

    result.fileResults.foreach { fileResult =>
      sb.append(formatFile(fileResult))
    }

    // Summary
    sb.append(color("\n--- Summary ---\n", Bold.On))
    sb.append(s"Files analyzed: ${result.analyzedFiles}/${result.totalFiles}\n")

    if (result.skippedFiles > 0) {
      sb.append(color(s"Files skipped (parse errors): ${result.skippedFiles}\n", Color.Yellow))
    }

    val errorStr = if (result.errorCount > 0) color(s"${result.errorCount}", Color.Red) else "0"
    val warnStr = if (result.warningCount > 0) color(s"${result.warningCount}", Color.Yellow) else "0"
    val infoStr = if (result.infoCount > 0) color(s"${result.infoCount}", Color.Blue) else "0"
    val hintStr = if (result.hintCount > 0) color(s"${result.hintCount}", Color.Cyan) else "0"

    sb.append(s"Issues found: $errorStr errors, $warnStr warnings, $infoStr info, $hintStr hints\n")

    if (result.hasErrors) {
      sb.append(color("\nAnalysis completed with errors.\n", Color.Red))
    } else if (result.warningCount > 0) {
      sb.append(color("\nAnalysis completed with warnings.\n", Color.Yellow))
    } else {
      sb.append(color("\nAnalysis completed successfully.\n", Color.Green))
    }

    sb.toString
  }
}

/**
 * JSON reporter
 */
class JsonReporter extends Reporter {

  // Circe encoders
  implicit val severityEncoder: Encoder[Severity] = Encoder.encodeString.contramap(_.name)
  implicit val categoryEncoder: Encoder[Category] = Encoder.encodeString.contramap(_.name)
  implicit val sourcePositionEncoder: Encoder[SourcePosition] = deriveEncoder[SourcePosition]
  implicit val lintIssueEncoder: Encoder[LintIssue] = deriveEncoder[LintIssue]
  implicit val fileResultEncoder: Encoder[FileAnalysisResult] = deriveEncoder[FileAnalysisResult]

  implicit val analysisResultEncoder: Encoder[AnalysisResult] = Encoder.instance { result =>
    Json.obj(
      "totalFiles" -> result.totalFiles.asJson,
      "analyzedFiles" -> result.analyzedFiles.asJson,
      "skippedFiles" -> result.skippedFiles.asJson,
      "totalIssues" -> result.totalIssueCount.asJson,
      "errorCount" -> result.errorCount.asJson,
      "warningCount" -> result.warningCount.asJson,
      "infoCount" -> result.infoCount.asJson,
      "hintCount" -> result.hintCount.asJson,
      "hasErrors" -> result.hasErrors.asJson,
      "files" -> result.fileResults.asJson
    )
  }

  def formatFile(result: FileAnalysisResult): String = {
    result.asJson.spaces2
  }

  def format(result: AnalysisResult): String = {
    result.asJson.spaces2
  }
}

/**
 * Compact one-line-per-issue reporter
 */
class CompactReporter extends Reporter {

  def formatFile(result: FileAnalysisResult): String = {
    val sb = new StringBuilder

    result.parseError.foreach { err =>
      sb.append(s"${result.file}:0:0: error: $err\n")
    }

    result.issues.foreach { issue =>
      val sev = issue.severity.name
      sb.append(s"${issue.position.file}:${issue.position.startLine}:${issue.position.startColumn}: $sev: [${issue.ruleId}] ${issue.message}\n")
    }

    sb.toString
  }

  def format(result: AnalysisResult): String = {
    result.fileResults.map(formatFile).mkString
  }
}

/**
 * GitHub Actions compatible reporter
 */
class GitHubReporter extends Reporter {

  private def ghSeverity(severity: Severity): String = severity match {
    case Severity.Error => "error"
    case Severity.Warning => "warning"
    case Severity.Info | Severity.Hint => "notice"
  }

  def formatFile(result: FileAnalysisResult): String = {
    val sb = new StringBuilder

    result.parseError.foreach { err =>
      sb.append(s"::error file=${result.file}::Parse error: $err\n")
    }

    result.issues.foreach { issue =>
      val sev = ghSeverity(issue.severity)
      val file = issue.position.file
      val line = issue.position.startLine
      val col = issue.position.startColumn
      val endLine = issue.position.endLine
      val endCol = issue.position.endColumn
      val title = s"[${issue.ruleId}] ${issue.ruleName}"
      val msg = issue.message.replace("\n", "%0A")

      sb.append(s"::$sev file=$file,line=$line,col=$col,endLine=$endLine,endColumn=$endCol,title=$title::$msg\n")
    }

    sb.toString
  }

  def format(result: AnalysisResult): String = {
    result.fileResults.map(formatFile).mkString
  }
}

/**
 * Checkstyle XML reporter
 */
class CheckstyleReporter extends Reporter {

  private def escape(s: String): String = {
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }

  private def severityName(severity: Severity): String = severity match {
    case Severity.Error => "error"
    case Severity.Warning => "warning"
    case Severity.Info | Severity.Hint => "info"
  }

  def formatFile(result: FileAnalysisResult): String = {
    val sb = new StringBuilder
    sb.append(s"""  <file name="${escape(result.file)}">\n""")

    result.parseError.foreach { err =>
      sb.append(s"""    <error line="0" column="0" severity="error" message="${escape(err)}" source="scalint.parser"/>\n""")
    }

    result.issues.foreach { issue =>
      val line = issue.position.startLine
      val col = issue.position.startColumn
      val sev = severityName(issue.severity)
      val msg = escape(issue.message)
      val source = s"scalint.${issue.ruleId}"

      sb.append(s"""    <error line="$line" column="$col" severity="$sev" message="$msg" source="$source"/>\n""")
    }

    sb.append("  </file>\n")
    sb.toString
  }

  def format(result: AnalysisResult): String = {
    val sb = new StringBuilder
    sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.append("\n")
    sb.append("""<checkstyle version="8.0">""")
    sb.append("\n")

    result.fileResults.foreach { fileResult =>
      sb.append(formatFile(fileResult))
    }

    sb.append("</checkstyle>\n")
    sb.toString
  }
}

/**
 * Reporter factory
 */
object Reporter {

  def create(format: OutputFormat, useColors: Boolean = true, showSuggestions: Boolean = true): Reporter = {
    format match {
      case OutputFormat.Text => new TextReporter(useColors, showSuggestions)
      case OutputFormat.Json => new JsonReporter()
      case OutputFormat.Compact => new CompactReporter()
      case OutputFormat.GitHub => new GitHubReporter()
      case OutputFormat.Checkstyle => new CheckstyleReporter()
    }
  }
}
