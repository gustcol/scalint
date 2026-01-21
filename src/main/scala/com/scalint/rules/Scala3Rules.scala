package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: Implicit to given/using migration
 */
object ImplicitToGivenRule extends Rule {
  val id = "SC3001"
  val name = "implicit-to-given"
  val description = "Consider migrating 'implicit' to 'given/using' for Scala 3"
  val category = Category.Scala3
  val severity = Severity.Info
  override val explanation = "Scala 3 introduces 'given' for implicit instances and 'using' for implicit " +
    "parameters. While 'implicit' still works, the new syntax is clearer and preferred."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect implicit def for typeclass instances
      case t @ Defn.Def(mods, _, _, _, _, _)
        if mods.exists {
          case Mod.Implicit() => true
          case _ => false
        } =>
        Seq(issue(
          "Consider using 'given' instead of 'implicit def' for Scala 3",
          t.pos,
          file,
          suggestion = Some("given instance: TypeClass[T] = new TypeClass[T] { ... }")
        ))

      // Detect implicit val for instances
      case t @ Defn.Val(mods, _, _, _)
        if mods.exists {
          case Mod.Implicit() => true
          case _ => false
        } =>
        Seq(issue(
          "Consider using 'given' instead of 'implicit val' for Scala 3",
          t.pos,
          file,
          suggestion = Some("given instance: TypeClass[T] = value")
        ))

      // Detect implicit class (Scala 3 uses extension methods)
      case t @ Defn.Class(mods, name, _, _, _)
        if mods.exists {
          case Mod.Implicit() => true
          case _ => false
        } =>
        Seq(issue(
          s"Consider using 'extension' methods instead of implicit class '${name.value}'",
          t.pos,
          file,
          suggestion = Some("extension (x: OriginalType) def newMethod = ...")
        ))

      // Detect implicit parameter lists
      case t @ Term.Function(params, _) =>
        params.collect {
          case param @ Term.Param(mods, _, _, _)
            if mods.exists {
              case Mod.Implicit() => true
              case _ => false
            } =>
            issue(
              "Consider using 'using' instead of 'implicit' parameter for Scala 3",
              param.pos,
              file,
              suggestion = Some("def method(using param: Type) = ...")
            )
        }
    }.flatten
  }
}

/**
 * Rule: Deprecated Scala 2 syntax
 */
object DeprecatedSyntaxRule extends Rule {
  val id = "SC3002"
  val name = "deprecated-scala2-syntax"
  val description = "Scala 2 syntax that is deprecated in Scala 3"
  val category = Category.Scala3
  val severity = Severity.Warning
  override val explanation = "Some Scala 2 syntax patterns are deprecated or have better alternatives in Scala 3."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect procedure syntax (def foo() { })
      case t @ Defn.Def(_, name, _, _, None, body) if !isUnitExplicit(body) =>
        // This would need more context to detect properly
        Seq.empty

      // Detect do-while loops
      case t @ Term.Do(_, _) =>
        Seq(issue(
          "do-while loops are discouraged in Scala 3",
          t.pos,
          file,
          suggestion = Some("Use while loops or tail recursion instead")
        ))

      // Detect XML literals (deprecated)
      case t @ Term.Xml(_, _) =>
        Seq(issue(
          "XML literals are deprecated; use a library like scala-xml",
          t.pos,
          file,
          suggestion = Some("Use scala-xml library or string interpolation")
        ))

      // Detect symbol literals 'symbol
      case t @ Lit.Symbol(_) =>
        Seq(issue(
          "Symbol literals are deprecated in Scala 3",
          t.pos,
          file,
          suggestion = Some("Use String or create an explicit Symbol: Symbol(\"name\")")
        ))

      // Detect () in nilary methods where not needed
      case t @ Term.Apply(Term.Name(name), Nil)
        if name.head.isLower && !name.startsWith("get") =>
        // This is too noisy, skip
        Seq.empty
    }.flatten
  }

  private def isUnitExplicit(body: Term): Boolean = {
    body match {
      case Lit.Unit() => true
      case _ => false
    }
  }
}

/**
 * Rule: Using wildcard imports from Scala 2
 */
object WildcardImportRule extends Rule {
  val id = "SC3003"
  val name = "wildcard-import-syntax"
  val description = "Scala 3 uses * instead of _ for wildcard imports"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3 uses 'import foo.*' instead of 'import foo._' for wildcard imports."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case i @ Import(importers) =>
        importers.flatMap { importer =>
          importer.importees.collect {
            case Importee.Wildcard() =>
              issue(
                "Scala 3 uses '*' for wildcard imports instead of '_'",
                i.pos,
                file,
                suggestion = Some(s"import ${importer.ref.syntax}.*")
              )
          }
        }
    }.flatten
  }
}

/**
 * Rule: Type lambda syntax
 */
object TypeLambdaSyntaxRule extends Rule {
  val id = "SC3004"
  val name = "type-lambda-syntax"
  val description = "Consider Scala 3's cleaner type lambda syntax"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3 introduces cleaner type lambda syntax: [X] =>> F[X] instead of ({type L[X] = F[X]})#L"

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect type projection pattern for type lambdas
      case t @ Type.Project(Type.Refine(_, stats), _) =>
        val hasTypeMember = stats.exists {
          case Decl.Type(_, _, _, _) => true
          case Defn.Type(_, _, _, _) => true
          case _ => false
        }
        if (hasTypeMember) {
          Seq(issue(
            "Consider Scala 3's type lambda syntax: [X] =>> F[X]",
            t.pos,
            file,
            suggestion = Some("Replace ({type L[X] = F[X]})#L with [X] =>> F[X]")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * Rule: Optional braces style
 */
object OptionalBracesRule extends Rule {
  val id = "SC3005"
  val name = "optional-braces"
  val description = "Scala 3 optional braces/indentation-based syntax"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3 supports optional braces with significant indentation. " +
    "Choose one style consistently across your codebase."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // This rule is informational and would need significant whitespace analysis
    // For now, we just provide awareness
    val sourceText = source.syntax
    val hasBraces = sourceText.count(_ == '{') > 5
    val hasColonEol = sourceText.contains(":\n")

    if (hasBraces && hasColonEol) {
      Seq(LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = Severity.Hint,
        message = "Mixed brace and indentation styles detected",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Choose either braces {} or significant indentation consistently"),
        explanation = Some(explanation)
      ))
    } else Seq.empty
  }
}

/**
 * Rule: Enum instead of sealed trait
 */
object EnumVsSealedRule extends Rule {
  val id = "SC3006"
  val name = "enum-vs-sealed"
  val description = "Consider using Scala 3 enum for simple ADTs"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3's enum is more concise for simple algebraic data types. " +
    "Use sealed trait for complex hierarchies with methods."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect sealed trait with only case object children
      case t @ Defn.Trait(mods, name, _, _, Template(_, _, _, stats))
        if mods.exists {
          case Mod.Sealed() => true
          case _ => false
        } && stats.forall {
          case _: Defn.Object => true
          case _: Defn.Class => true
          case _ => false
        } && stats.nonEmpty =>
        // Check if the children are simple (no methods/vals)
        val isSimpleAdt = stats.forall {
          case Defn.Object(_, _, Template(_, _, _, objStats)) =>
            objStats.isEmpty || objStats.forall {
              case _: Defn.Val => true
              case _ => false
            }
          case Defn.Class(_, _, _, _, Template(_, _, _, clsStats)) =>
            clsStats.isEmpty
          case _ => true
        }

        if (isSimpleAdt) {
          Seq(issue(
            s"Sealed trait '${name.value}' could be simplified to Scala 3 enum",
            t.pos,
            file,
            suggestion = Some(s"enum ${name.value} { case A, B, C }")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * Rule: Export clause usage
 */
object ExportClauseRule extends Rule {
  val id = "SC3007"
  val name = "export-clause"
  val description = "Consider Scala 3's export clause for delegation"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3's export clause provides a cleaner way to expose members of composed objects."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect delegation pattern: def method = delegate.method
      case cls @ Defn.Class(_, className, _, _, Template(_, _, _, stats)) =>
        val delegationCount = stats.count {
          case Defn.Def(_, _, _, _, _, Term.Select(Term.Name(_), _)) => true
          case _ => false
        }

        if (delegationCount >= 3) {
          Seq(issue(
            s"Class '${className.value}' has $delegationCount delegation methods",
            cls.pos,
            file,
            suggestion = Some("Consider using Scala 3's 'export delegate.*' for cleaner delegation")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * Rule: Union/Intersection types
 */
object UnionIntersectionTypesRule extends Rule {
  val id = "SC3008"
  val name = "union-intersection-types"
  val description = "Consider Scala 3's union (|) and intersection (&) types"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3 has native union (A | B) and intersection (A & B) types " +
    "that can replace some uses of Either and with/compound types."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect Either for union-like types
      case t @ Type.Apply(Type.Name("Either"), List(left, right))
        if isSimpleType(left) && isSimpleType(right) =>
        Seq(issue(
          "Consider Scala 3's union type: A | B instead of Either[A, B]",
          t.pos,
          file,
          suggestion = Some(s"${left.syntax} | ${right.syntax}")
        ))

      // Detect compound types with with
      case t @ Type.With(left, right) =>
        Seq(issue(
          "Consider Scala 3's intersection type: A & B",
          t.pos,
          file,
          suggestion = Some(s"${left.syntax} & ${right.syntax}")
        ))
    }.flatten
  }

  private def isSimpleType(t: Type): Boolean = t match {
    case _: Type.Name => true
    case _ => false
  }
}

/**
 * Rule: Context functions
 */
object ContextFunctionRule extends Rule {
  val id = "SC3009"
  val name = "context-function"
  val description = "Consider Scala 3's context functions for cleaner APIs"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3's context functions (T ?=> R) provide a cleaner way to pass " +
    "implicit context than curried implicit parameter lists."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect methods with curried implicit parameters
      case t @ Defn.Def(_, name, _, paramss, _, _) if paramss.size >= 2 =>
        val hasImplicitList = paramss.exists { params =>
          params.exists { param =>
            param.mods.exists {
              case Mod.Implicit() => true
              case _ => false
            }
          }
        }
        if (hasImplicitList) {
          Seq(issue(
            s"Method '${name.value}' has curried implicit parameters - consider context function",
            t.pos,
            file,
            suggestion = Some("def method: Context ?=> Result = ...")
          ))
        } else Seq.empty
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule: Opaque types
 */
object OpaqueTypeRule extends Rule {
  val id = "SC3010"
  val name = "opaque-type"
  val description = "Consider Scala 3's opaque types for zero-cost type wrappers"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3's opaque types provide zero-cost abstractions for type safety. " +
    "They're more efficient than value classes for simple wrappers."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect value classes (extends AnyVal)
      case t @ Defn.Class(mods, name, _, ctor, Template(_, inits, _, stats))
        if inits.exists(_.tpe.syntax.contains("AnyVal")) =>
        val fields = ctor.paramss.flatten
        if (fields.size == 1 && stats.isEmpty) {
          Seq(issue(
            s"Value class '${name.value}' can be replaced with Scala 3 opaque type",
            t.pos,
            file,
            suggestion = Some(s"opaque type ${name.value} = ${fields.head.decltpe.map(_.syntax).getOrElse("???")}")
          ))
        } else Seq.empty

      // Detect tagged types pattern (shapeless-style)
      case t @ Defn.Type(_, name, _, Type.With(_, Type.Name("Tagged"))) =>
        Seq(issue(
          s"Tagged type '${name.value}' can be replaced with Scala 3 opaque type",
          t.pos,
          file,
          suggestion = Some(s"opaque type ${name.value} = ...")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule: Inline and transparent inline
 */
object InlineRule extends Rule {
  val id = "SC3011"
  val name = "inline-optimization"
  val description = "Consider Scala 3's inline for compile-time optimization"
  val category = Category.Scala3
  val severity = Severity.Info
  override val explanation = "Scala 3's inline keyword guarantees inlining at compile time. " +
    "Use it for performance-critical small methods or compile-time metaprogramming."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect @inline annotation
      case t @ Defn.Def(mods, name, _, _, _, body)
        if mods.exists {
          case Mod.Annot(Init(Type.Name("inline"), _, _)) => true
          case _ => false
        } =>
        Seq(issue(
          s"@inline annotation on '${name.value}' - consider Scala 3's inline keyword",
          t.pos,
          file,
          suggestion = Some("inline def instead of @inline def")
        ))

      // Detect small constant methods that could be inline
      case t @ Defn.Def(_, name, _, paramss, _, body)
        if paramss.flatten.isEmpty && isConstant(body) =>
        Seq(issue(
          s"Constant method '${name.value}' could use inline val in Scala 3",
          t.pos,
          file,
          suggestion = Some("inline val or inline def for compile-time evaluation")
        ))

      case _ => Seq.empty
    }.flatten
  }

  private def isConstant(body: Term): Boolean = body match {
    case _: Lit => true
    case Term.Apply(Term.Name(n), _) if n == "List" || n == "Seq" || n == "Set" => true
    case _ => false
  }
}

/**
 * Rule: Match types
 */
object MatchTypeRule extends Rule {
  val id = "SC3012"
  val name = "match-type"
  val description = "Complex type-level programming may benefit from Scala 3's match types"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3's match types provide cleaner type-level pattern matching " +
    "than type class encodings or type projections."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect type projections that might be match types
      case t @ Defn.Type(_, name, tparams, Type.Project(_, _)) if tparams.nonEmpty =>
        Seq(issue(
          s"Type alias '${name.value}' with projection may be cleaner as match type",
          t.pos,
          file,
          suggestion = Some("type Elem[X] = X match { case List[t] => t case ... }")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule: Open class modifier
 */
object OpenClassRule extends Rule {
  val id = "SC3013"
  val name = "open-class"
  val description = "Consider marking inheritance explicitly with 'open' in Scala 3"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3 encourages explicit opt-in to inheritance with the 'open' modifier. " +
    "Classes are effectively final by default unless marked 'open'."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect non-sealed, non-final, non-abstract classes that might need 'open'
      case t @ Defn.Class(mods, name, _, _, Template(_, inits, _, stats))
        if !mods.exists {
          case Mod.Final() | Mod.Sealed() | Mod.Abstract() => true
          case _ => false
        } && inits.isEmpty && stats.exists {
          case _: Defn.Def => true
          case _ => false
        } =>
        Seq(issue(
          s"Class '${name.value}' is open for extension - consider marking 'open', 'sealed', or 'final' in Scala 3",
          t.pos,
          file,
          suggestion = Some("open class / sealed class / final class for explicit intent")
        ))
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule: Main method annotation
 */
object MainAnnotationRule extends Rule {
  val id = "SC3014"
  val name = "main-annotation"
  val description = "Consider Scala 3's @main annotation for entry points"
  val category = Category.Scala3
  val severity = Severity.Hint
  override val explanation = "Scala 3's @main annotation provides a simpler way to define entry points " +
    "with automatic argument parsing, replacing the traditional main method pattern."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect traditional main method
      case t @ Defn.Def(_, Term.Name("main"), _, List(List(param)), _, _)
        if param.decltpe.exists(_.syntax.contains("Array[String]")) =>
        Seq(issue(
          "Traditional main method - consider @main annotation in Scala 3",
          t.pos,
          file,
          suggestion = Some("@main def myApp(arg1: String, arg2: Int): Unit = ...")
        ))

      // Detect App trait extension
      case t @ Defn.Object(_, name, Template(_, inits, _, _))
        if inits.exists(_.tpe.syntax == "App") =>
        Seq(issue(
          s"Object '${name.value}' extends App - consider @main annotation in Scala 3",
          t.pos,
          file,
          suggestion = Some("@main def ${name.value}(): Unit = ...")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * All Scala 3 rules
 */
object Scala3Rules {
  val all: Seq[Rule] = Seq(
    ImplicitToGivenRule,
    DeprecatedSyntaxRule,
    WildcardImportRule,
    TypeLambdaSyntaxRule,
    OptionalBracesRule,
    EnumVsSealedRule,
    ExportClauseRule,
    UnionIntersectionTypesRule,
    ContextFunctionRule,
    OpaqueTypeRule,
    InlineRule,
    MatchTypeRule,
    OpenClassRule,
    MainAnnotationRule
  )
}
