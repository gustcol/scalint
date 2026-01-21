package com.scalint.baseline

import com.scalint.core._
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.io.Source
import scala.util.{Try, Using}

/**
 * Baseline Manager for Legacy Projects
 *
 * Baselines allow teams to adopt ScalaLint gradually on existing codebases.
 * Issues recorded in the baseline are ignored, so only NEW issues are reported.
 *
 * Workflow:
 * 1. Run `scalint --generate-baseline` to capture current issues
 * 2. Commit .scalint-baseline.json to version control
 * 3. Future runs only report new issues
 * 4. Gradually fix baseline issues and regenerate
 *
 * The baseline tracks issues by:
 * - File path (relative)
 * - Rule ID
 * - Line content hash (to handle line number changes)
 */
object BaselineManager {

  val BaselineFileName = ".scalint-baseline.json"

  /**
   * A baseline entry - represents a single ignored issue
   */
  case class BaselineEntry(
    file: String,
    ruleId: String,
    lineHash: String,
    message: String,
    originalLine: Int,
    createdAt: Long = System.currentTimeMillis()
  )

  /**
   * The baseline - a collection of ignored issues
   */
  case class Baseline(
    version: String = "1.0",
    generatedAt: Long = System.currentTimeMillis(),
    entries: Seq[BaselineEntry] = Seq.empty
  ) {
    def isEmpty: Boolean = entries.isEmpty
    def size: Int = entries.size

    def containsIssue(issue: LintIssue, fileContent: String): Boolean = {
      val lineContent = getLineContent(fileContent, issue.position.startLine)
      val hash = computeHash(lineContent)

      entries.exists { entry =>
        entry.file == issue.position.file &&
        entry.ruleId == issue.ruleId &&
        entry.lineHash == hash
      }
    }
  }

  /**
   * Generate a baseline from current issues
   */
  def generateBaseline(
    analysisResult: AnalysisResult,
    fileContents: Map[String, String]
  ): Baseline = {
    val entries = analysisResult.allIssues.map { issue =>
      val content = fileContents.getOrElse(issue.position.file, "")
      val lineContent = getLineContent(content, issue.position.startLine)

      BaselineEntry(
        file = issue.position.file,
        ruleId = issue.ruleId,
        lineHash = computeHash(lineContent),
        message = issue.message,
        originalLine = issue.position.startLine
      )
    }

    Baseline(entries = entries)
  }

  /**
   * Save baseline to file
   */
  def saveBaseline(baseline: Baseline, path: String = BaselineFileName): Boolean = {
    Try {
      val json = serializeBaseline(baseline)
      val writer = new PrintWriter(new File(path))
      try {
        writer.write(json)
        true
      } finally {
        writer.close()
      }
    }.getOrElse(false)
  }

  /**
   * Load baseline from file
   */
  def loadBaseline(path: String = BaselineFileName): Option[Baseline] = {
    val file = new File(path)
    if (!file.exists()) {
      return None
    }

    Try {
      Using.resource(Source.fromFile(file)) { source =>
        deserializeBaseline(source.mkString)
      }
    }.toOption.flatten
  }

  /**
   * Filter issues against baseline - returns only NEW issues
   */
  def filterNewIssues(
    issues: Seq[LintIssue],
    baseline: Baseline,
    fileContents: Map[String, String]
  ): Seq[LintIssue] = {
    issues.filterNot { issue =>
      val content = fileContents.getOrElse(issue.position.file, "")
      baseline.containsIssue(issue, content)
    }
  }

  /**
   * Filter analysis result against baseline
   */
  def filterAnalysisResult(
    result: AnalysisResult,
    baseline: Baseline,
    fileContents: Map[String, String]
  ): AnalysisResult = {
    val filteredFileResults = result.fileResults.map { fileResult =>
      val content = fileContents.getOrElse(fileResult.file, "")
      val filteredIssues = fileResult.issues.filterNot { issue =>
        baseline.containsIssue(issue, content)
      }
      fileResult.copy(issues = filteredIssues)
    }

    result.copy(fileResults = filteredFileResults)
  }

  /**
   * Get statistics about baseline
   */
  case class BaselineStats(
    totalEntries: Int,
    byRule: Map[String, Int],
    byFile: Map[String, Int],
    oldestEntry: Option[Long],
    newestEntry: Option[Long]
  )

  def getStats(baseline: Baseline): BaselineStats = {
    BaselineStats(
      totalEntries = baseline.entries.size,
      byRule = baseline.entries.groupBy(_.ruleId).map { case (k, v) => k -> v.size },
      byFile = baseline.entries.groupBy(_.file).map { case (k, v) => k -> v.size },
      oldestEntry = baseline.entries.map(_.createdAt).minOption,
      newestEntry = baseline.entries.map(_.createdAt).maxOption
    )
  }

  /**
   * Compute hash of a line for comparison
   */
  private def computeHash(content: String): String = {
    val normalized = content.trim.replaceAll("\\s+", " ")
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(normalized.getBytes("UTF-8"))
    bytes.map("%02x".format(_)).mkString
  }

  /**
   * Get content of a specific line
   */
  private def getLineContent(fileContent: String, lineNumber: Int): String = {
    val lines = fileContent.split("\n", -1)
    if (lineNumber > 0 && lineNumber <= lines.length) {
      lines(lineNumber - 1)
    } else {
      ""
    }
  }

  /**
   * Serialize baseline to JSON (simple implementation)
   */
  private def serializeBaseline(baseline: Baseline): String = {
    val entriesJson = baseline.entries.map { entry =>
      s"""    {
         |      "file": "${escape(entry.file)}",
         |      "ruleId": "${entry.ruleId}",
         |      "lineHash": "${entry.lineHash}",
         |      "message": "${escape(entry.message)}",
         |      "originalLine": ${entry.originalLine},
         |      "createdAt": ${entry.createdAt}
         |    }""".stripMargin
    }.mkString(",\n")

    s"""{
       |  "version": "${baseline.version}",
       |  "generatedAt": ${baseline.generatedAt},
       |  "entries": [
       |$entriesJson
       |  ]
       |}""".stripMargin
  }

  private def escape(s: String): String = {
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }

  /**
   * Deserialize baseline from JSON (simple implementation)
   */
  private def deserializeBaseline(json: String): Option[Baseline] = {
    Try {
      // Simple regex-based parsing (use a JSON library in production)
      val versionRegex = """"version"\s*:\s*"([^"]+)"""".r
      val generatedAtRegex = """"generatedAt"\s*:\s*(\d+)""".r
      val entryRegex = """\{\s*"file"\s*:\s*"([^"]+)"\s*,\s*"ruleId"\s*:\s*"([^"]+)"\s*,\s*"lineHash"\s*:\s*"([^"]+)"\s*,\s*"message"\s*:\s*"([^"]+)"\s*,\s*"originalLine"\s*:\s*(\d+)\s*,\s*"createdAt"\s*:\s*(\d+)\s*\}""".r

      val version = versionRegex.findFirstMatchIn(json).map(_.group(1)).getOrElse("1.0")
      val generatedAt = generatedAtRegex.findFirstMatchIn(json).map(_.group(1).toLong).getOrElse(0L)

      val entries = entryRegex.findAllMatchIn(json).map { m =>
        BaselineEntry(
          file = unescape(m.group(1)),
          ruleId = m.group(2),
          lineHash = m.group(3),
          message = unescape(m.group(4)),
          originalLine = m.group(5).toInt,
          createdAt = m.group(6).toLong
        )
      }.toSeq

      Some(Baseline(version, generatedAt, entries))
    }.toOption.flatten
  }

  private def unescape(s: String): String = {
    s.replace("\\\\", "\\")
      .replace("\\\"", "\"")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
  }

  /**
   * Clean up stale baseline entries (issues that no longer exist)
   */
  def cleanBaseline(
    baseline: Baseline,
    currentIssues: Seq[LintIssue],
    fileContents: Map[String, String]
  ): Baseline = {
    val stillRelevant = baseline.entries.filter { entry =>
      // Check if there's a current issue that matches this baseline entry
      currentIssues.exists { issue =>
        val content = fileContents.getOrElse(issue.position.file, "")
        val lineContent = getLineContent(content, issue.position.startLine)
        val hash = computeHash(lineContent)

        entry.file == issue.position.file &&
        entry.ruleId == issue.ruleId &&
        entry.lineHash == hash
      }
    }

    baseline.copy(entries = stillRelevant, generatedAt = System.currentTimeMillis())
  }
}
