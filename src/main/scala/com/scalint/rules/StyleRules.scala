package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: Class names should be in PascalCase
 */
object ClassNamingRule extends Rule {
  val id = "S001"
  val name = "class-naming"
  val description = "Class names should be in PascalCase"
  val category = Category.Style
  val severity = Severity.Warning
  override val explanation = "Class names should start with an uppercase letter and use PascalCase (e.g., MyClass, HttpClient)"

  private val pascalCaseRegex = "^[A-Z][a-zA-Z0-9]*$".r

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case cls: Defn.Class if !pascalCaseRegex.matches(cls.name.value) =>
        issue(
          s"Class name '${cls.name.value}' should be in PascalCase",
          cls.name.pos,
          file,
          suggestion = Some(toPascalCase(cls.name.value))
        )
    }
  }

  private def toPascalCase(s: String): String = {
    s.split("_").map(_.capitalize).mkString
  }
}

/**
 * Rule: Object names should be in PascalCase
 */
object ObjectNamingRule extends Rule {
  val id = "S002"
  val name = "object-naming"
  val description = "Object names should be in PascalCase"
  val category = Category.Style
  val severity = Severity.Warning
  override val explanation = "Object names should start with an uppercase letter and use PascalCase"

  private val pascalCaseRegex = "^[A-Z][a-zA-Z0-9]*$".r

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case obj: Defn.Object if !pascalCaseRegex.matches(obj.name.value) =>
        issue(
          s"Object name '${obj.name.value}' should be in PascalCase",
          obj.name.pos,
          file
        )
    }
  }
}

/**
 * Rule: Method names should be in camelCase
 */
object MethodNamingRule extends Rule {
  val id = "S003"
  val name = "method-naming"
  val description = "Method names should be in camelCase"
  val category = Category.Style
  val severity = Severity.Warning
  override val explanation = "Method names should start with a lowercase letter and use camelCase (e.g., getUserById, calculateTotal)"

  private val camelCaseRegex = "^[a-z][a-zA-Z0-9]*$".r
  private val operatorRegex = "^[!#%&*+\\-/:<=>?@\\\\^|~]+$".r
  private val specialNames = Set("apply", "unapply", "unapplySeq", "update", "toString", "hashCode", "equals", "copy")

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case d: Defn.Def
        if !camelCaseRegex.matches(d.name.value) &&
           !operatorRegex.matches(d.name.value) &&
           !specialNames.contains(d.name.value) &&
           !d.name.value.startsWith("_") =>
        issue(
          s"Method name '${d.name.value}' should be in camelCase",
          d.name.pos,
          file,
          suggestion = Some(toCamelCase(d.name.value))
        )
    }
  }

  private def toCamelCase(s: String): String = {
    val parts = s.split("_")
    parts.head.toLowerCase + parts.tail.map(_.capitalize).mkString
  }
}

/**
 * Rule: Variable names should be in camelCase
 */
object VariableNamingRule extends Rule {
  val id = "S004"
  val name = "variable-naming"
  val description = "Variable names should be in camelCase"
  val category = Category.Style
  val severity = Severity.Warning
  override val explanation = "Variable names should start with a lowercase letter and use camelCase"

  private val camelCaseRegex = "^[a-z_][a-zA-Z0-9]*$".r
  private val constantRegex = "^[A-Z][A-Z0-9_]*$".r

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case v: Defn.Var =>
        v.pats.collect {
          case Pat.Var(name) if !camelCaseRegex.matches(name.value) && !constantRegex.matches(name.value) =>
            issue(
              s"Variable name '${name.value}' should be in camelCase",
              name.pos,
              file
            )
        }
      case v: Defn.Val =>
        v.pats.collect {
          case Pat.Var(name) if !camelCaseRegex.matches(name.value) && !constantRegex.matches(name.value) =>
            issue(
              s"Val name '${name.value}' should be in camelCase or UPPER_SNAKE_CASE for constants",
              name.pos,
              file
            )
        }
    }.flatten
  }
}

/**
 * Rule: Constants should be in UPPER_SNAKE_CASE
 */
object ConstantNamingRule extends Rule {
  val id = "S005"
  val name = "constant-naming"
  val description = "Constants in companion objects should be in UPPER_SNAKE_CASE"
  val category = Category.Style
  val severity = Severity.Info
  override val explanation = "Constants defined in companion objects should use UPPER_SNAKE_CASE"

  private val upperSnakeRegex = "^[A-Z][A-Z0-9_]*$".r

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case obj: Defn.Object =>
        obj.templ.stats.collect {
          case v: Defn.Val if v.mods.exists {
            case Mod.Final() => true
            case _ => false
          } =>
            v.pats.collect {
              case Pat.Var(name) if !upperSnakeRegex.matches(name.value) =>
                issue(
                  s"Constant '${name.value}' should be in UPPER_SNAKE_CASE",
                  name.pos,
                  file
                )
            }
        }.flatten
    }.flatten
  }
}

/**
 * Rule: Avoid using return statements
 */
object AvoidReturnRule extends Rule {
  val id = "S006"
  val name = "avoid-return"
  val description = "Avoid using explicit return statements"
  val category = Category.Style
  val severity = Severity.Warning
  override val explanation = "In Scala, the last expression in a method is automatically returned. Explicit return statements can lead to unexpected behavior, especially in closures."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case ret: Term.Return =>
        issue(
          "Avoid using explicit return statements; use the last expression as the return value",
          ret.pos,
          file,
          suggestion = Some("Remove 'return' and let the expression be the implicit return value")
        )
    }
  }
}

/**
 * Rule: Use string interpolation instead of concatenation
 */
object StringInterpolationRule extends Rule {
  val id = "S007"
  val name = "string-interpolation"
  val description = "Prefer string interpolation over concatenation"
  val category = Category.Style
  val severity = Severity.Info
  override val explanation = "String interpolation (s\"Hello $name\") is more readable than concatenation (\"Hello \" + name)"

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case infix @ Term.ApplyInfix(lhs: Lit.String, Term.Name("+"), _, _) =>
        issue(
          "Consider using string interpolation instead of concatenation",
          infix.pos,
          file,
          suggestion = Some("Use s\"...${expr}\" for string interpolation")
        )
      case infix @ Term.ApplyInfix(_, Term.Name("+"), _, List(_: Lit.String)) if isStringContext(infix) =>
        issue(
          "Consider using string interpolation instead of concatenation",
          infix.pos,
          file
        )
    }
  }

  private def isStringContext(tree: Tree): Boolean = tree match {
    case Term.ApplyInfix(lhs: Lit.String, _, _, _) => true
    case Term.ApplyInfix(lhs: Term.ApplyInfix, _, _, _) => isStringContext(lhs)
    case _ => false
  }
}

/**
 * Rule: Line length should not exceed limit
 */
object LineLengthRule extends Rule {
  val id = "S008"
  val name = "line-length"
  val description = "Lines should not exceed 120 characters"
  val category = Category.Style
  val severity = Severity.Info
  override val explanation = "Long lines are harder to read. Keep lines under 120 characters for better readability."

  private val maxLength = 120

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val lines = source.syntax.split("\n")
    lines.zipWithIndex.collect {
      case (line, idx) if line.length > maxLength =>
        LintIssue(
          ruleId = id,
          ruleName = name,
          category = category,
          severity = severity,
          message = s"Line exceeds $maxLength characters (${line.length})",
          position = SourcePosition(file, idx + 1, maxLength + 1, idx + 1, line.length),
          suggestion = Some("Consider breaking this line into multiple lines"),
          explanation = Some(explanation)
        )
    }.toSeq
  }
}

/**
 * Rule: Avoid procedure syntax
 */
object AvoidProcedureSyntaxRule extends Rule {
  val id = "S009"
  val name = "avoid-procedure-syntax"
  val description = "Avoid procedure syntax (def foo() { ... })"
  val category = Category.Style
  val severity = Severity.Warning
  override val explanation = "Procedure syntax is deprecated. Use explicit return type annotation with Unit."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case d: Defn.Def if d.decltpe.isEmpty && isProcedureSyntax(d) =>
        issue(
          "Avoid procedure syntax; use explicit ': Unit =' instead",
          d.pos,
          file,
          suggestion = Some(s"def ${d.name}(...): Unit = { ... }")
        )
    }
  }

  private def isProcedureSyntax(d: Defn.Def): Boolean = {
    d.body match {
      case Term.Block(_) => d.decltpe.isEmpty
      case _ => false
    }
  }
}

/**
 * Rule: Use meaningful parameter names
 */
object MeaningfulParameterNamesRule extends Rule {
  val id = "S010"
  val name = "meaningful-param-names"
  val description = "Parameter names should be meaningful"
  val category = Category.Style
  val severity = Severity.Info
  override val explanation = "Single-letter parameter names (except common ones like i, j, k for loops) reduce code readability."

  private val allowedShortNames = Set("i", "j", "k", "n", "m", "x", "y", "z", "a", "b", "f", "t", "e", "_")

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case d: Defn.Def =>
        d.paramss.flatten.collect {
          case param: Term.Param
            if param.name.value.length == 1 &&
               !allowedShortNames.contains(param.name.value) =>
            issue(
              s"Parameter name '${param.name.value}' is too short; use a meaningful name",
              param.name.pos,
              file
            )
        }
    }.flatten
  }
}

/**
 * Rule: Avoid wildcard imports
 */
object AvoidWildcardImportsRule extends Rule {
  val id = "S011"
  val name = "avoid-wildcard-imports"
  val description = "Avoid wildcard imports"
  val category = Category.Style
  val severity = Severity.Info
  override val explanation = "Wildcard imports can pollute the namespace and make it unclear where symbols come from."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case i: Import =>
        i.importers.flatMap { importer =>
          importer.importees.collect {
            case Importee.Wildcard() =>
              issue(
                s"Avoid wildcard import: ${importer.ref.syntax}._",
                i.pos,
                file,
                suggestion = Some("Import specific symbols instead")
              )
          }
        }
    }.flatten
  }
}

/**
 * All style rules
 */
object StyleRules {
  val all: Seq[Rule] = Seq(
    ClassNamingRule,
    ObjectNamingRule,
    MethodNamingRule,
    VariableNamingRule,
    ConstantNamingRule,
    AvoidReturnRule,
    StringInterpolationRule,
    LineLengthRule,
    AvoidProcedureSyntaxRule,
    MeaningfulParameterNamesRule,
    AvoidWildcardImportsRule
  )
}
