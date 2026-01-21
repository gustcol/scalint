package com.scalint.core

import scala.meta._

/**
 * Base trait for all lint rules
 */
trait Rule {
  /** Unique identifier for the rule */
  def id: String

  /** Human-readable name */
  def name: String

  /** Description of what the rule checks */
  def description: String

  /** Category of the rule */
  def category: Category

  /** Default severity */
  def severity: Severity

  /** Detailed explanation of the rule */
  def explanation: String = description

  /** Check a parsed source file */
  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue]

  /** Create an issue from this rule */
  protected def issue(
    message: String,
    position: Position,
    file: String,
    suggestion: Option[String] = None,
    codeSnippet: Option[String] = None
  ): LintIssue = {
    LintIssue(
      ruleId = id,
      ruleName = name,
      category = category,
      severity = severity,
      message = message,
      position = SourcePosition.fromMeta(position, file),
      suggestion = suggestion,
      codeSnippet = codeSnippet,
      explanation = Some(explanation)
    )
  }

  /** Helper to extract code snippet from source */
  protected def extractSnippet(source: Source, pos: Position, context: Int = 1): Option[String] = {
    try {
      val lines = source.syntax.split("\n")
      val start = math.max(0, pos.startLine - context)
      val end = math.min(lines.length - 1, pos.endLine + context)
      Some(lines.slice(start, end + 1).mkString("\n"))
    } catch {
      case _: Exception => None
    }
  }
}

/**
 * Trait for rules that traverse the AST
 */
trait TreeRule extends Rule {
  override def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      case tree => checkTree(tree, source, file, config).foreach(issues += _)
    }

    issues.toSeq
  }

  /** Check a single tree node */
  def checkTree(tree: Tree, source: Source, file: String, config: LintConfig): Seq[LintIssue]
}

/**
 * Trait for rules that do pattern matching on specific tree types
 */
trait PatternRule extends Rule {
  override def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case tree if matches(tree) => createIssue(tree, source, file, config)
    }.flatten
  }

  /** Check if the tree matches this rule */
  def matches(tree: Tree): Boolean

  /** Create issue(s) for a matching tree */
  def createIssue(tree: Tree, source: Source, file: String, config: LintConfig): Seq[LintIssue]
}

/**
 * Trait for rules that analyze the whole source at once
 */
trait SourceRule extends Rule {
  // Uses the default check method, subclasses implement their own logic
}

/**
 * Rule context for passing additional information
 */
case class RuleContext(
  source: Source,
  file: String,
  config: LintConfig,
  imports: Seq[Import] = Seq.empty,
  packageName: Option[String] = None
)

object RuleContext {
  def from(source: Source, file: String, config: LintConfig): RuleContext = {
    val imports = source.collect { case i: Import => i }
    val packageName = source.collect {
      case pkg: Pkg => pkg.ref.syntax
    }.headOption

    RuleContext(source, file, config, imports, packageName)
  }
}

/**
 * Represents a code fix that can be automatically applied
 */
case class CodeFix(
  description: String,
  startLine: Int,
  startColumn: Int,
  endLine: Int,
  endColumn: Int,
  replacement: String,
  isPreferred: Boolean = true
)

/**
 * Trait for issues that can be automatically fixed
 */
case class FixableIssue(
  issue: LintIssue,
  fix: CodeFix
)

/**
 * Trait for rules that can provide auto-fixes
 */
trait FixableRule extends Rule {

  /**
   * Check source and return fixable issues (issues with fixes)
   */
  def checkWithFix(source: Source, file: String, config: LintConfig): Seq[FixableIssue]

  /**
   * Default implementation calls checkWithFix and extracts just the issues
   */
  override def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    checkWithFix(source, file, config).map(_.issue)
  }

  /**
   * Helper to create a FixableIssue
   */
  protected def fixableIssue(
    message: String,
    position: Position,
    file: String,
    fix: CodeFix,
    suggestion: Option[String] = None
  ): FixableIssue = {
    FixableIssue(
      issue = LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = message,
        position = SourcePosition.fromMeta(position, file),
        suggestion = suggestion.orElse(Some(s"Auto-fix: ${fix.description}")),
        codeSnippet = None,
        explanation = Some(explanation)
      ),
      fix = fix
    )
  }
}

/**
 * Auto-fixer that applies fixes to source code
 */
object AutoFixer {

  /**
   * Apply a single fix to source code
   */
  def applyFix(sourceCode: String, fix: CodeFix): String = {
    val lines = sourceCode.split("\n", -1)

    if (fix.startLine == fix.endLine) {
      // Single-line fix
      val line = lines(fix.startLine - 1)
      val newLine = line.substring(0, fix.startColumn - 1) +
        fix.replacement +
        line.substring(fix.endColumn - 1)
      lines(fix.startLine - 1) = newLine
      lines.mkString("\n")
    } else {
      // Multi-line fix
      val beforeFix = lines.take(fix.startLine - 1).toSeq
      val startLine = lines(fix.startLine - 1)
      val endLine = lines(fix.endLine - 1)

      val newContent = startLine.substring(0, fix.startColumn - 1) +
        fix.replacement +
        endLine.substring(fix.endColumn - 1)

      val afterFix = lines.drop(fix.endLine).toSeq

      (beforeFix :+ newContent) ++ afterFix mkString "\n"
    }
  }

  /**
   * Apply multiple fixes to source code (sorted by position, applied from end to start)
   */
  def applyFixes(sourceCode: String, fixes: Seq[CodeFix]): String = {
    // Sort fixes from end to start to avoid position shifts
    val sortedFixes = fixes.sortBy(f => (-f.startLine, -f.startColumn))

    sortedFixes.foldLeft(sourceCode) { (code, fix) =>
      applyFix(code, fix)
    }
  }

  /**
   * Result of applying fixes to a file
   */
  case class FixResult(
    file: String,
    originalContent: String,
    fixedContent: String,
    appliedFixes: Int,
    skippedFixes: Int
  ) {
    def hasChanges: Boolean = originalContent != fixedContent
  }

  /**
   * Apply all available fixes to a file
   */
  def fixFile(
    file: String,
    content: String,
    fixableIssues: Seq[FixableIssue],
    dryRun: Boolean = false
  ): FixResult = {
    val fixes = fixableIssues.map(_.fix)
    val fixedContent = applyFixes(content, fixes)

    FixResult(
      file = file,
      originalContent = content,
      fixedContent = fixedContent,
      appliedFixes = fixes.size,
      skippedFixes = 0
    )
  }
}
