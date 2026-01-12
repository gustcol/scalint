package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: Prefer immutable collections
 */
object PreferImmutableCollectionsRule extends Rule {
  val id = "F001"
  val name = "prefer-immutable-collections"
  val description = "Prefer immutable collections over mutable ones"
  val category = Category.FunctionalStyle
  val severity = Severity.Info
  override val explanation = "Immutable collections are easier to reason about and safer in concurrent code."

  private val mutableCollections = Set(
    "ArrayBuffer", "ListBuffer", "mutable.Map", "mutable.Set",
    "mutable.Seq", "mutable.HashMap", "mutable.HashSet",
    "mutable.ArrayStack", "mutable.Queue", "mutable.PriorityQueue"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Type.Name(name) if mutableCollections.exists(name.contains) =>
        issue(
          s"Consider using immutable collection instead of $name",
          t.pos,
          file,
          suggestion = Some("Use immutable collections from scala.collection.immutable")
        )
      case i: Import if i.syntax.contains("scala.collection.mutable") =>
        issue(
          "Importing mutable collections; prefer immutable alternatives",
          i.pos,
          file,
          suggestion = Some("Use immutable collections unless mutability is required")
        )
    }
  }
}

/**
 * Rule: Avoid side effects in map/filter/flatMap
 */
object SideEffectsInMapRule extends Rule {
  val id = "F002"
  val name = "side-effects-in-map"
  val description = "Avoid side effects in map/filter/flatMap operations"
  val category = Category.FunctionalStyle
  val severity = Severity.Warning
  override val explanation = "Side effects in functional transformations make code harder to reason about and test."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect assignments or println inside map/filter/flatMap
      case t @ Term.Apply(Term.Select(_, Term.Name(op)), List(func))
        if Set("map", "filter", "flatMap", "foreach").contains(op) =>
        findSideEffects(func).map { se =>
          issue(
            s"Side effect detected in $op operation: ${se.syntax.take(30)}...",
            t.pos,
            file,
            suggestion = Some("Use foreach for side effects, or extract side effects from pure transformations")
          )
        }
    }.flatten
  }

  private def findSideEffects(tree: Tree): Seq[Tree] = {
    tree.collect {
      case t @ Term.Assign(_, _) => t
      case t @ Term.Apply(Term.Select(_, Term.Name("println")), _) => t
      case t @ Term.Apply(Term.Name("println"), _) => t
      case t @ Term.Apply(Term.Select(_, Term.Name("print")), _) => t
    }
  }
}

/**
 * Rule: Prefer pattern matching over isInstanceOf/asInstanceOf
 */
object PreferPatternMatchingRule extends Rule {
  val id = "F003"
  val name = "prefer-pattern-matching"
  val description = "Prefer pattern matching over isInstanceOf/asInstanceOf"
  val category = Category.FunctionalStyle
  val severity = Severity.Warning
  override val explanation = "Pattern matching is type-safe and more idiomatic in Scala than isInstanceOf/asInstanceOf casts."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.ApplyType(Term.Select(_, Term.Name("isInstanceOf")), _) =>
        issue(
          "Prefer pattern matching over isInstanceOf",
          t.pos,
          file,
          suggestion = Some("Use: x match { case _: Type => ... case _ => ... }")
        )
      case t @ Term.ApplyType(Term.Select(_, Term.Name("asInstanceOf")), _) =>
        issue(
          "Prefer pattern matching over asInstanceOf",
          t.pos,
          file,
          suggestion = Some("Use: x match { case t: Type => /* use t */ case _ => ... }")
        )
    }
  }
}

/**
 * Rule: Prefer fold/reduce over var with foreach
 */
object PreferFoldReduceRule extends Rule {
  val id = "F004"
  val name = "prefer-fold-reduce"
  val description = "Prefer fold/reduce over var accumulator with foreach"
  val category = Category.FunctionalStyle
  val severity = Severity.Info
  override val explanation = "fold and reduce are more functional and often clearer than accumulator patterns."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      case Term.Block(stats) =>
        val varNames = stats.collect {
          case Defn.Var(_, pats, _, _) =>
            pats.collect { case Pat.Var(name) => name.value }
        }.flatten.toSet

        stats.collect {
          case t @ Term.Apply(Term.Select(_, Term.Name("foreach")), List(func)) =>
            func.collect {
              case Term.Assign(Term.Name(name), _) if varNames.contains(name) =>
                issues += issue(
                  s"Accumulator pattern with var '$name'; consider using fold or reduce",
                  t.pos,
                  file,
                  suggestion = Some("Replace with: collection.foldLeft(initial)((acc, elem) => ...)")
                )
            }
        }
    }

    issues.toSeq
  }
}

/**
 * Rule: Use Option instead of null checks
 */
object UseOptionRule extends Rule {
  val id = "F005"
  val name = "use-option"
  val description = "Use Option instead of null checks"
  val category = Category.FunctionalStyle
  val severity = Severity.Warning
  override val explanation = "Option provides a type-safe way to handle absence of values and integrates well with functional patterns."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: if (x != null) Some(x) else None
      case t @ Term.If(
            Term.ApplyInfix(_, Term.Name("!="), _, List(Lit.Null())),
            Term.Apply(Term.Name("Some"), _),
            Term.Name("None")) =>
        issue(
          "Use Option(x) instead of if (x != null) Some(x) else None",
          t.pos,
          file,
          suggestion = Some("Replace with Option(x)")
        )
      // Detect: if (x == null) None else Some(x)
      case t @ Term.If(
            Term.ApplyInfix(_, Term.Name("=="), _, List(Lit.Null())),
            Term.Name("None"),
            Term.Apply(Term.Name("Some"), _)) =>
        issue(
          "Use Option(x) instead of if (x == null) None else Some(x)",
          t.pos,
          file,
          suggestion = Some("Replace with Option(x)")
        )
    }
  }
}

/**
 * Rule: Avoid using while loops
 */
object AvoidWhileLoopsRule extends Rule {
  val id = "F006"
  val name = "avoid-while-loops"
  val description = "Consider functional alternatives to while loops"
  val category = Category.FunctionalStyle
  val severity = Severity.Hint
  override val explanation = "While loops require mutable state. Consider using recursion, fold, or Iterator for functional style."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t: Term.While =>
        issue(
          "Consider functional alternatives to while loop",
          t.pos,
          file,
          suggestion = Some("Use @tailrec recursion, Iterator, or LazyList")
        )
    }
  }
}

/**
 * Rule: Prefer for-comprehension over nested flatMap/map
 */
object PreferForComprehensionRule extends Rule {
  val id = "F007"
  val name = "prefer-for-comprehension"
  val description = "Consider using for-comprehension for nested flatMap/map"
  val category = Category.FunctionalStyle
  val severity = Severity.Hint
  override val explanation = "For-comprehensions are often more readable than deeply nested flatMap/map chains."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: x.flatMap(a => y.flatMap(b => z.map(c => ...)))
      case t @ Term.Apply(
            Term.Select(_, Term.Name("flatMap")),
            List(Term.Function(_, Term.Apply(Term.Select(_, Term.Name("flatMap")), _)))) =>
        issue(
          "Nested flatMap/map chain; consider using for-comprehension",
          t.pos,
          file,
          suggestion = Some("Use: for { a <- x; b <- y; c <- z } yield ...")
        )
    }
  }
}

/**
 * Rule: Use partial functions with collect
 */
object UseCollectRule extends Rule {
  val id = "F008"
  val name = "use-collect"
  val description = "Use collect instead of filter + map"
  val category = Category.FunctionalStyle
  val severity = Severity.Info
  override val explanation = "collect combines filter and map in a single operation using partial functions."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: x.filter(p).map(f) or x.flatMap(... if ...)
      case t @ Term.Apply(
            Term.Select(
              Term.Apply(Term.Select(_, Term.Name("filter")), _),
              Term.Name("map")),
            _) =>
        issue(
          "Use collect instead of filter followed by map",
          t.pos,
          file,
          suggestion = Some("Use: collection.collect { case x if predicate(x) => transform(x) }")
        )
    }
  }
}

/**
 * Rule: Avoid using Any or AnyRef explicitly
 */
object AvoidAnyRule extends Rule {
  val id = "F009"
  val name = "avoid-any"
  val description = "Avoid using Any or AnyRef explicitly in type annotations"
  val category = Category.FunctionalStyle
  val severity = Severity.Warning
  override val explanation = "Using Any or AnyRef loses type safety. Use proper generic types or algebraic data types."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val defIssues = source.collect {
      case d: Defn.Def =>
        d.decltpe match {
          case Some(t @ Type.Name("Any")) =>
            Some(issue(
              "Avoid using Any as return type; use specific types or generics",
              t.pos,
              file,
              suggestion = Some("Define a sealed trait or use generic type parameter")
            ))
          case Some(t @ Type.Name("AnyRef")) =>
            Some(issue(
              "Avoid using AnyRef as return type; use specific types or generics",
              t.pos,
              file,
              suggestion = Some("Define a sealed trait or use generic type parameter")
            ))
          case _ => None
        }
    }.flatten

    val valIssues = source.collect {
      case d: Defn.Val =>
        d.decltpe match {
          case Some(t @ Type.Name("Any")) =>
            Some(issue(
              "Avoid using Any as type annotation; use specific types",
              t.pos,
              file,
              suggestion = Some("Use proper type instead of Any")
            ))
          case _ => None
        }
    }.flatten

    defIssues ++ valIssues
  }
}

/**
 * Rule: Use case classes for data
 */
object UseCaseClassRule extends Rule {
  val id = "F010"
  val name = "use-case-class"
  val description = "Consider using case class for data-only classes"
  val category = Category.FunctionalStyle
  val severity = Severity.Hint
  override val explanation = "Case classes provide equals, hashCode, copy, and pattern matching support automatically."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case cls: Defn.Class if !isCaseClass(cls) && isDataOnlyClass(cls) =>
        issue(
          "Consider using case class for this data-only class",
          cls.pos,
          file,
          suggestion = Some("Add 'case' modifier: case class ${cls.name.value}(...)")
        )
    }
  }

  private def isCaseClass(cls: Defn.Class): Boolean = {
    cls.mods.exists {
      case Mod.Case() => true
      case _ => false
    }
  }

  private def isDataOnlyClass(cls: Defn.Class): Boolean = {
    val hasOnlyVals = cls.ctor.paramss.flatten.forall { param =>
      !param.mods.exists {
        case Mod.VarParam() => true
        case _ => false
      }
    }

    val hasNoMethods = cls.templ.stats.forall {
      case _: Defn.Def => false
      case _ => true
    }

    hasOnlyVals && hasNoMethods && cls.ctor.paramss.flatten.nonEmpty
  }
}

/**
 * All functional style rules
 */
object FunctionalRules {
  val all: Seq[Rule] = Seq(
    PreferImmutableCollectionsRule,
    SideEffectsInMapRule,
    PreferPatternMatchingRule,
    PreferFoldReduceRule,
    UseOptionRule,
    AvoidWhileLoopsRule,
    PreferForComprehensionRule,
    UseCollectRule,
    AvoidAnyRule,
    UseCaseClassRule
  )
}
