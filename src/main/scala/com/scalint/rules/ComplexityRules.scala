package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Complexity Analysis rules for detecting overly complex code
 */

/**
 * Rule CX001: Method too long
 */
object MethodTooLongRule extends Rule {
  val id = "CX001"
  val name = "method-too-long"
  val description = "Methods should not exceed a reasonable line count"
  val category = Category.Complexity
  val severity = Severity.Warning
  override val explanation = "Long methods are harder to understand, test, and maintain. " +
    "Consider extracting logical parts into smaller, well-named helper methods. " +
    "Aim for methods that fit on one screen (typically 20-30 lines)."

  private val maxLines = 50

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Defn.Def(_, name, _, _, _, body) =>
        val lineCount = body.pos.endLine - body.pos.startLine + 1
        if (lineCount > maxLines) {
          Seq(issue(
            s"Method '${name.value}' is $lineCount lines - consider splitting into smaller methods",
            t.pos,
            file,
            suggestion = Some(s"Extract logical sections into helper methods (max recommended: $maxLines lines)")
          ))
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule CX002: Too many parameters
 */
object TooManyParametersRule extends Rule {
  val id = "CX002"
  val name = "too-many-parameters"
  val description = "Functions with too many parameters are hard to use and maintain"
  val category = Category.Complexity
  val severity = Severity.Warning
  override val explanation = "Methods with many parameters are error-prone (argument order confusion) " +
    "and indicate the method might be doing too much. Consider using a case class or builder pattern " +
    "to group related parameters."

  private val maxParams = 6

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Defn.Def(_, name, _, paramss, _, _) =>
        val totalParams = paramss.flatten.size
        if (totalParams > maxParams) {
          Seq(issue(
            s"Method '${name.value}' has $totalParams parameters (max: $maxParams)",
            t.pos,
            file,
            suggestion = Some("Group related parameters into a case class or use a builder pattern")
          ))
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule CX003: Cyclomatic complexity too high
 * Simplified: counts branches (if, match cases, loops)
 */
object CyclomaticComplexityRule extends Rule {
  val id = "CX003"
  val name = "cyclomatic-complexity"
  val description = "High cyclomatic complexity indicates hard-to-test code"
  val category = Category.Complexity
  val severity = Severity.Warning
  override val explanation = "Cyclomatic complexity measures the number of independent paths through code. " +
    "High complexity (>10) makes testing difficult as each path needs a test case. " +
    "Break complex logic into smaller functions or use pattern matching strategically."

  private val maxComplexity = 10

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Defn.Def(_, name, _, _, _, body) =>
        val complexity = calculateComplexity(body)
        if (complexity > maxComplexity) {
          Seq(issue(
            s"Method '${name.value}' has cyclomatic complexity of $complexity (max: $maxComplexity)",
            t.pos,
            file,
            suggestion = Some("Extract conditional branches into helper methods")
          ))
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }

  private def calculateComplexity(tree: Tree): Int = {
    var complexity = 1 // Base complexity

    tree.traverse {
      case _: Term.If => complexity += 1
      case _: Term.While => complexity += 1
      case _: Term.For => complexity += 1
      case _: Term.ForYield => complexity += 1
      case _: Term.Try => complexity += 1
      case Term.Match(_, cases) => complexity += cases.size - 1
      case Term.PartialFunction(cases) => complexity += cases.size - 1
      case Term.ApplyInfix(_, Term.Name("&&"), _, _) => complexity += 1
      case Term.ApplyInfix(_, Term.Name("||"), _, _) => complexity += 1
      case _ =>
    }

    complexity
  }
}

/**
 * Rule CX004: Nested depth too high
 */
object NestedDepthRule extends Rule {
  val id = "CX004"
  val name = "nested-depth"
  val description = "Deeply nested code is hard to follow"
  val category = Category.Complexity
  val severity = Severity.Warning
  override val explanation = "Deep nesting (if within if within if...) creates a 'pyramid of doom' " +
    "that's hard to read and understand. Use early returns, guard clauses, or extract nested logic " +
    "into separate methods."

  private val maxDepth = 4

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    def checkNesting(tree: Tree, currentDepth: Int, file: String): Unit = {
      if (currentDepth > maxDepth) {
        issues += issue(
          s"Nesting depth of $currentDepth exceeds maximum ($maxDepth)",
          tree.pos,
          file,
          suggestion = Some("Use early returns or extract nested logic into helper methods")
        )
      }

      tree.children.foreach {
        case t: Term.If => checkNesting(t, currentDepth + 1, file)
        case t: Term.Match => checkNesting(t, currentDepth + 1, file)
        case t: Term.While => checkNesting(t, currentDepth + 1, file)
        case t: Term.For => checkNesting(t, currentDepth + 1, file)
        case t: Term.ForYield => checkNesting(t, currentDepth + 1, file)
        case t: Term.Try => checkNesting(t, currentDepth + 1, file)
        case t => checkNesting(t, currentDepth, file)
      }
    }

    source.traverse {
      case t: Defn.Def =>
        checkNesting(t.body, 0, file)
      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule CX005: Class too large
 */
object ClassTooLargeRule extends Rule {
  val id = "CX005"
  val name = "class-too-large"
  val description = "Large classes violate Single Responsibility Principle"
  val category = Category.Complexity
  val severity = Severity.Warning
  override val explanation = "Large classes typically have too many responsibilities. " +
    "Consider splitting into smaller, focused classes. A class should have one reason to change."

  private val maxMethods = 20
  private val maxLines = 300

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Defn.Class(_, name, _, _, template) =>
        val methods = template.stats.collect { case _: Defn.Def => 1 }.size
        val lines = t.pos.endLine - t.pos.startLine + 1
        val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

        if (methods > maxMethods) {
          issues += issue(
            s"Class '${name.value}' has $methods methods (max: $maxMethods)",
            t.pos,
            file,
            suggestion = Some("Consider splitting into smaller, focused classes")
          )
        }

        if (lines > maxLines) {
          issues += issue(
            s"Class '${name.value}' is $lines lines (max: $maxLines)",
            t.pos,
            file,
            suggestion = Some("Break down into smaller classes with single responsibility")
          )
        }

        issues.toSeq

      case t @ Defn.Object(_, name, template) =>
        val methods = template.stats.collect { case _: Defn.Def => 1 }.size
        val lines = t.pos.endLine - t.pos.startLine + 1

        if (methods > maxMethods || lines > maxLines) {
          Seq(issue(
            s"Object '${name.value}' is too large ($methods methods, $lines lines)",
            t.pos,
            file,
            suggestion = Some("Consider splitting functionality into separate objects or traits")
          ))
        } else {
          Seq.empty
        }

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule CX006: Too many case classes in file
 */
object TooManyCaseClassesRule extends Rule {
  val id = "CX006"
  val name = "too-many-case-classes"
  val description = "Files with many case classes may need reorganization"
  val category = Category.Complexity
  val severity = Severity.Info
  override val explanation = "While ADTs (sealed trait + case classes) are good, too many " +
    "unrelated case classes in one file indicates poor organization. Consider grouping " +
    "related types in separate files."

  private val maxCaseClasses = 10

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val caseClasses = source.collect {
      case Defn.Class(mods, _, _, _, _) if mods.exists(_.isInstanceOf[Mod.Case]) => 1
      case _ => 0
    }.sum

    if (caseClasses > maxCaseClasses) {
      Seq(LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = s"File contains $caseClasses case classes (max: $maxCaseClasses)",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Split related case classes into separate files by domain"),
        explanation = Some(explanation)
      ))
    } else {
      Seq.empty
    }
  }
}

/**
 * Rule CX007: Boolean parameters (flag arguments)
 */
object BooleanParameterRule extends Rule {
  val id = "CX007"
  val name = "boolean-parameter"
  val description = "Boolean parameters reduce code clarity at call sites"
  val category = Category.Complexity
  val severity = Severity.Info
  override val explanation = "Boolean parameters make call sites unclear: doSomething(true, false) " +
    "doesn't convey meaning. Consider using named parameters, enums, or separate methods: " +
    "doSomethingQuietly() vs doSomethingVerbose()."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Defn.Def(_, name, _, paramss, _, _) =>
        val boolParams = paramss.flatten.collect {
          case Term.Param(_, paramName, Some(Type.Name("Boolean")), _) => paramName.value
        }

        if (boolParams.size >= 2) {
          Seq(issue(
            s"Method '${name.value}' has ${boolParams.size} boolean parameters: ${boolParams.mkString(", ")}",
            t.pos,
            file,
            suggestion = Some("Consider using an enum, separate methods, or named parameters")
          ))
        } else {
          Seq.empty
        }

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule CX008: Long parameter lists in case class
 */
object LargeCaseClassRule extends Rule {
  val id = "CX008"
  val name = "large-case-class"
  val description = "Case classes with many fields are hard to work with"
  val category = Category.Complexity
  val severity = Severity.Info
  override val explanation = "Case classes with many fields become unwieldy, especially when using copy(). " +
    "Consider grouping related fields into nested case classes or using a builder pattern."

  private val maxFields = 10

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Defn.Class(mods, name, _, ctor, _) if mods.exists(_.isInstanceOf[Mod.Case]) =>
        val fieldCount = ctor.paramss.flatten.size
        if (fieldCount > maxFields) {
          Seq(issue(
            s"Case class '${name.value}' has $fieldCount fields (max: $maxFields)",
            t.pos,
            file,
            suggestion = Some("Group related fields into nested case classes")
          ))
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule CX009: Magic numbers
 */
object MagicNumberRule extends Rule {
  val id = "CX009"
  val name = "magic-number"
  val description = "Magic numbers should be extracted as named constants"
  val category = Category.Complexity
  val severity = Severity.Info
  override val explanation = "Magic numbers (literal numbers in code) obscure meaning. " +
    "Extract them as named constants: val MaxRetries = 3 makes intent clear."

  // Ignore common acceptable literals
  private val allowedNumbers = Set(0, 1, -1, 2, 10, 100, 1000)

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Lit.Int(value) if !allowedNumbers.contains(value) && value > 1 =>
        // Check if it's part of a val definition (constant)
        t.parent match {
          case Some(_: Defn.Val) => Seq.empty // Already a constant
          case Some(_: Defn.Var) => Seq.empty
          case Some(Term.Assign(_, _)) => Seq.empty
          case _ =>
            Seq(issue(
              s"Magic number $value - consider extracting as named constant",
              t.pos,
              file,
              suggestion = Some(s"val SomeMeaningfulName = $value")
            ))
        }

      case t @ Lit.Double(value) if value != 0.0 && value != 1.0 && value != 0.5 =>
        t.parent match {
          case Some(_: Defn.Val) => Seq.empty
          case Some(_: Defn.Var) => Seq.empty
          case _ =>
            Seq(issue(
              s"Magic number $value - consider extracting as named constant",
              t.pos,
              file,
              suggestion = Some(s"val SomeMeaningfulName = $value")
            ))
        }

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule CX010: Feature envy (method uses more external data than own class)
 * Simplified heuristic
 */
object FeatureEnvyRule extends Rule {
  val id = "CX010"
  val name = "feature-envy"
  val description = "Method may belong in a different class"
  val category = Category.Complexity
  val severity = Severity.Hint
  override val explanation = "Feature envy occurs when a method uses more data from another object " +
    "than from its own class. This suggests the method might belong in that other class instead."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Defn.Def(_, name, _, _, _, body) =>
        // Count method calls on same variable
        val externalCalls = scala.collection.mutable.Map[String, Int]()

        body.traverse {
          case Term.Select(Term.Name(obj), Term.Name(_)) =>
            externalCalls(obj) = externalCalls.getOrElse(obj, 0) + 1
          case _ =>
        }

        val mostUsed = externalCalls.maxByOption(_._2)
        mostUsed match {
          case Some((obj, count)) if count >= 5 =>
            Seq(issue(
              s"Method '${name.value}' calls $count methods on '$obj' - possible feature envy",
              t.pos,
              file,
              suggestion = Some(s"Consider moving this method to the $obj class")
            ))
          case _ => Seq.empty
        }

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * All Complexity rules
 */
object ComplexityRules {
  val all: Seq[Rule] = Seq(
    MethodTooLongRule,
    TooManyParametersRule,
    CyclomaticComplexityRule,
    NestedDepthRule,
    ClassTooLargeRule,
    TooManyCaseClassesRule,
    BooleanParameterRule,
    LargeCaseClassRule,
    MagicNumberRule,
    FeatureEnvyRule
  )
}
