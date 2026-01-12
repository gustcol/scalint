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
