package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: Avoid null usage
 */
object AvoidNullRule extends Rule {
  val id = "B001"
  val name = "avoid-null"
  val description = "Avoid using null; use Option instead"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Null references can cause NullPointerExceptions. Use Option[T] to represent optional values."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case lit: Lit.Null =>
        issue(
          "Avoid using null; use Option[T] instead",
          lit.pos,
          file,
          suggestion = Some("Use None for absent values, Some(value) for present values")
        )
      case t @ Term.ApplyInfix(_, Term.Name("=="), _, List(Lit.Null())) =>
        issue(
          "Avoid comparing with null; use Option methods like isEmpty or isDefined",
          t.pos,
          file,
          suggestion = Some("Wrap in Option and use .isEmpty or pattern matching")
        )
      case t @ Term.ApplyInfix(_, Term.Name("!="), _, List(Lit.Null())) =>
        issue(
          "Avoid comparing with null; use Option methods like nonEmpty or isDefined",
          t.pos,
          file,
          suggestion = Some("Wrap in Option and use .isDefined or pattern matching")
        )
    }
  }
}

/**
 * Rule: Avoid .get on Option
 */
object AvoidOptionGetRule extends Rule {
  val id = "B002"
  val name = "avoid-option-get"
  val description = "Avoid using .get on Option; use getOrElse, map, or pattern matching"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Calling .get on None throws NoSuchElementException. Use safer alternatives like getOrElse, fold, or pattern matching."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Select(qual, Term.Name("get")) if isOptionContext(qual) =>
        issue(
          "Avoid using .get on Option; it throws NoSuchElementException if empty",
          t.pos,
          file,
          suggestion = Some("Use .getOrElse(default), .fold, .map, or pattern matching")
        )
    }
  }

  private def isOptionContext(tree: Tree): Boolean = {
    tree.syntax.contains("Option") || tree.syntax.endsWith("Opt")
  }
}

/**
 * Rule: Avoid .head on collections
 */
object AvoidHeadRule extends Rule {
  val id = "B003"
  val name = "avoid-head"
  val description = "Avoid using .head on collections; use .headOption instead"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Calling .head on an empty collection throws NoSuchElementException. Use .headOption for safety."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Select(_, Term.Name("head")) =>
        issue(
          "Using .head on collections can throw NoSuchElementException on empty collections",
          t.pos,
          file,
          suggestion = Some("Use .headOption instead, which returns Option[T]")
        )
    }
  }
}

/**
 * Rule: Avoid .last on collections
 */
object AvoidLastRule extends Rule {
  val id = "B004"
  val name = "avoid-last"
  val description = "Avoid using .last on collections; use .lastOption instead"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Calling .last on an empty collection throws NoSuchElementException. Use .lastOption for safety."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Select(_, Term.Name("last")) =>
        issue(
          "Using .last on collections can throw NoSuchElementException on empty collections",
          t.pos,
          file,
          suggestion = Some("Use .lastOption instead, which returns Option[T]")
        )
    }
  }
}

/**
 * Rule: Avoid throwing exceptions in pure functions
 */
object AvoidThrowingExceptionsRule extends Rule {
  val id = "B005"
  val name = "avoid-throwing"
  val description = "Consider using Either or Try instead of throwing exceptions"
  val category = Category.Bug
  val severity = Severity.Info
  override val explanation = "Throwing exceptions breaks referential transparency. Consider using Either[Error, T] or Try[T] for better error handling."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t: Term.Throw =>
        issue(
          "Consider using Either or Try instead of throwing exceptions",
          t.pos,
          file,
          suggestion = Some("Return Either[Error, T] or Try[T] for functional error handling")
        )
    }
  }
}

/**
 * Rule: Detect unreachable code
 */
object UnreachableCodeRule extends Rule {
  val id = "B006"
  val name = "unreachable-code"
  val description = "Detect unreachable code after return/throw"
  val category = Category.Bug
  val severity = Severity.Error
  override val explanation = "Code after return or throw statements will never be executed."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case Term.Block(stats) =>
        findUnreachableAfter(stats, file)
    }.flatten
  }

  private def findUnreachableAfter(stats: List[Stat], file: String): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var foundTerminator = false

    stats.foreach { stat =>
      if (foundTerminator) {
        issues += issue(
          "Unreachable code detected",
          stat.pos,
          file
        )
      }
      stat match {
        case _: Term.Return | _: Term.Throw =>
          foundTerminator = true
        case _ =>
      }
    }

    issues.toSeq
  }
}

/**
 * Rule: Non-exhaustive pattern matching
 */
object NonExhaustiveMatchRule extends Rule {
  val id = "B007"
  val name = "non-exhaustive-match"
  val description = "Pattern matching should be exhaustive or have a catch-all case"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Non-exhaustive pattern matching can throw MatchError at runtime."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case m: Term.Match if !hasWildcardCase(m.cases) && m.cases.nonEmpty =>
        issue(
          "Pattern matching may not be exhaustive; consider adding a catch-all case",
          m.pos,
          file,
          suggestion = Some("Add a wildcard case: case _ => ...")
        )
    }
  }

  private def hasWildcardCase(cases: List[Case]): Boolean = {
    cases.exists { c =>
      c.pat match {
        case Pat.Wildcard() => true
        case Pat.Var(_) => true
        case _ => false
      }
    }
  }
}

/**
 * Rule: Comparing floating point numbers with ==
 */
object FloatComparisonRule extends Rule {
  val id = "B008"
  val name = "float-comparison"
  val description = "Avoid comparing floating-point numbers with == or !="
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Floating-point comparison using == can fail due to precision issues. Use epsilon comparison instead."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.ApplyInfix(_: Lit.Double, Term.Name(op), _, _) if op == "==" || op == "!=" =>
        issue(
          s"Avoid comparing floating-point numbers with $op; use epsilon comparison",
          t.pos,
          file,
          suggestion = Some("Use Math.abs(a - b) < epsilon for comparison")
        )
      case t @ Term.ApplyInfix(_, Term.Name(op), _, List(_: Lit.Double)) if op == "==" || op == "!=" =>
        issue(
          s"Avoid comparing floating-point numbers with $op; use epsilon comparison",
          t.pos,
          file,
          suggestion = Some("Use Math.abs(a - b) < epsilon for comparison")
        )
    }
  }
}

/**
 * Rule: Avoid var in case class
 */
object VarInCaseClassRule extends Rule {
  val id = "B009"
  val name = "var-in-case-class"
  val description = "Avoid using var in case class parameters"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Case classes should be immutable. Using var breaks the assumptions of pattern matching and copy method."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case cls: Defn.Class if cls.mods.exists { case Mod.Case() => true; case _ => false } =>
        cls.ctor.paramss.flatten.collect {
          case param: Term.Param if param.mods.exists { case Mod.VarParam() => true; case _ => false } =>
            issue(
              s"Avoid using 'var' in case class parameter '${param.name.value}'",
              param.pos,
              file,
              suggestion = Some("Use 'val' (default for case classes) for immutability")
            )
        }
    }.flatten
  }
}

/**
 * Rule: Avoid mutable collections in public APIs
 */
object MutableCollectionInApiRule extends Rule {
  val id = "B010"
  val name = "mutable-collection-api"
  val description = "Avoid exposing mutable collections in public APIs"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Exposing mutable collections allows external code to modify internal state unexpectedly."

  private val mutableTypes = Set(
    "ArrayBuffer", "ListBuffer", "StringBuilder", "mutable.Map", "mutable.Set",
    "mutable.Seq", "mutable.List", "mutable.HashMap", "mutable.HashSet",
    "MutableList", "LinkedList", "DoubleLinkedList"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case d: Defn.Def if !isPrivate(d.mods) && returnsMutableCollection(d) =>
        issue(
          "Public method returns a mutable collection; consider returning an immutable collection",
          d.pos,
          file,
          suggestion = Some("Return an immutable collection or use .toList, .toSeq, etc.")
        )
    }
  }

  private def isPrivate(mods: List[Mod]): Boolean = {
    mods.exists {
      case Mod.Private(_) => true
      case _ => false
    }
  }

  private def returnsMutableCollection(d: Defn.Def): Boolean = {
    d.decltpe.exists { tpe =>
      mutableTypes.exists(t => tpe.syntax.contains(t))
    }
  }
}

/**
 * Rule: Detect empty catch blocks
 */
object EmptyCatchBlockRule extends Rule {
  val id = "B011"
  val name = "empty-catch-block"
  val description = "Avoid empty catch blocks"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Empty catch blocks silently swallow exceptions, making debugging difficult."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case Term.Try(_, cases, _) =>
        cases.collect {
          case c: Case if isEmpty(c.body) =>
            issue(
              "Empty catch block; exceptions are silently swallowed",
              c.pos,
              file,
              suggestion = Some("Log the exception or handle it appropriately")
            )
        }
    }.flatten
  }

  private def isEmpty(tree: Tree): Boolean = tree match {
    case Term.Block(Nil) => true
    case Lit.Unit() => true
    case _ => false
  }
}

/**
 * Rule: Detect comparison of Boolean with literal
 */
object BooleanComparisonRule extends Rule {
  val id = "B012"
  val name = "boolean-comparison"
  val description = "Avoid comparing Boolean values with true/false literals"
  val category = Category.Bug
  val severity = Severity.Info
  override val explanation = "Comparing a Boolean with true or false is redundant. Use the Boolean directly."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.ApplyInfix(_, Term.Name("=="), _, List(Lit.Boolean(_))) =>
        issue(
          "Redundant comparison with Boolean literal",
          t.pos,
          file,
          suggestion = Some("Use the Boolean expression directly: 'if (x)' instead of 'if (x == true)'")
        )
      case t @ Term.ApplyInfix(Lit.Boolean(_), Term.Name("=="), _, _) =>
        issue(
          "Redundant comparison with Boolean literal",
          t.pos,
          file,
          suggestion = Some("Use the Boolean expression directly")
        )
    }
  }
}

/**
 * Rule: Avoid shadowing outer variables
 */
object ShadowingRule extends Rule {
  val id = "B013"
  val name = "shadowing"
  val description = "Avoid shadowing outer scope variables"
  val category = Category.Bug
  val severity = Severity.Warning
  override val explanation = "Shadowing can lead to confusion and bugs when the inner variable is mistakenly used instead of the outer one."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    def checkShadowing(tree: Tree, outerScope: Set[String]): Unit = {
      tree match {
        case d: Defn.Def =>
          // Create new scope for method parameters
          var methodScope = outerScope
          d.paramss.flatten.foreach { p =>
            val paramName = p.name.value
            if (outerScope.contains(paramName)) {
              issues += issue(
                s"Parameter '$paramName' shadows an outer scope binding",
                p.pos,
                file,
                suggestion = Some("Use a different name to avoid confusion")
              )
            }
            methodScope = methodScope + paramName
          }
          // Check method body with combined scope
          d.body match {
            case Term.Block(stats) =>
              checkBlock(stats, methodScope)
            case other =>
              checkShadowing(other, methodScope)
          }

        case Term.Block(stats) =>
          checkBlock(stats, outerScope)

        case Term.Function(params, body) =>
          var funcScope = outerScope
          params.foreach { p =>
            val paramName = p.name.value
            if (outerScope.contains(paramName)) {
              issues += issue(
                s"Parameter '$paramName' shadows an outer scope binding",
                p.pos,
                file,
                suggestion = Some("Use a different name to avoid confusion")
              )
            }
            funcScope = funcScope + paramName
          }
          checkShadowing(body, funcScope)

        case c: Case =>
          val patNames = extractPatternNames(c.pat)
          var caseScope = outerScope
          patNames.foreach { case (name, pos) =>
            if (outerScope.contains(name)) {
              issues += issue(
                s"Pattern variable '$name' shadows an outer scope binding",
                pos,
                file,
                suggestion = Some("Use a different name to avoid confusion")
              )
            }
            caseScope = caseScope + name
          }
          c.cond.foreach(checkShadowing(_, caseScope))
          checkShadowing(c.body, caseScope)

        case f: Term.For =>
          var forScope = outerScope
          f.enums.foreach {
            case Enumerator.Generator(pat, rhs) =>
              checkShadowing(rhs, forScope)
              val patNames = extractPatternNames(pat)
              patNames.foreach { case (name, pos) =>
                if (outerScope.contains(name)) {
                  issues += issue(
                    s"Generator variable '$name' shadows an outer scope binding",
                    pos,
                    file,
                    suggestion = Some("Use a different name to avoid confusion")
                  )
                }
                forScope = forScope + name
              }
            case Enumerator.Val(pat, rhs) =>
              checkShadowing(rhs, forScope)
              val patNames = extractPatternNames(pat)
              patNames.foreach { case (name, _) =>
                forScope = forScope + name
              }
            case Enumerator.Guard(cond) =>
              checkShadowing(cond, forScope)
          }
          checkShadowing(f.body, forScope)

        case _ =>
          tree.children.foreach(checkShadowing(_, outerScope))
      }
    }

    def checkBlock(stats: List[Stat], outerScope: Set[String]): Unit = {
      var blockScope = outerScope
      stats.foreach {
        case d: Defn.Val =>
          checkShadowing(d.rhs, blockScope)
          d.pats.foreach {
            case Pat.Var(name) =>
              if (outerScope.contains(name.value)) {
                issues += issue(
                  s"Variable '${name.value}' shadows an outer scope binding",
                  name.pos,
                  file,
                  suggestion = Some("Use a different name to avoid confusion")
                )
              }
              blockScope = blockScope + name.value
            case _ =>
          }
        case d: Defn.Var =>
          d.rhs.foreach(checkShadowing(_, blockScope))
          d.pats.foreach {
            case Pat.Var(name) =>
              if (outerScope.contains(name.value)) {
                issues += issue(
                  s"Variable '${name.value}' shadows an outer scope binding",
                  name.pos,
                  file,
                  suggestion = Some("Use a different name to avoid confusion")
                )
              }
              blockScope = blockScope + name.value
            case _ =>
          }
        case other =>
          checkShadowing(other, blockScope)
      }
    }

    def extractPatternNames(pat: Pat): Seq[(String, Position)] = {
      pat match {
        case Pat.Var(name) => Seq((name.value, name.pos))
        case Pat.Tuple(args) => args.flatMap(extractPatternNames)
        case Pat.Extract(_, args) => args.flatMap(extractPatternNames)
        case Pat.Typed(p, _) => extractPatternNames(p)
        case Pat.Bind(Pat.Var(name), p) => (name.value, name.pos) +: extractPatternNames(p)
        case _ => Seq.empty
      }
    }

    // Start checking from the source
    source.stats.foreach(checkShadowing(_, Set.empty))

    issues.toSeq
  }
}

/**
 * All bug detection rules
 */
object BugRules {
  val all: Seq[Rule] = Seq(
    AvoidNullRule,
    AvoidOptionGetRule,
    AvoidHeadRule,
    AvoidLastRule,
    AvoidThrowingExceptionsRule,
    UnreachableCodeRule,
    NonExhaustiveMatchRule,
    FloatComparisonRule,
    VarInCaseClassRule,
    MutableCollectionInApiRule,
    EmptyCatchBlockRule,
    BooleanComparisonRule,
    ShadowingRule
  )
}
