package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: Avoid using size == 0 for emptiness check
 */
object UseSizeEqualsZeroRule extends Rule {
  val id = "P001"
  val name = "use-isempty"
  val description = "Use isEmpty instead of size == 0 or length == 0"
  val category = Category.Performance
  val severity = Severity.Warning
  override val explanation = "isEmpty is more efficient as it can short-circuit after checking the first element, while size must count all elements."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.ApplyInfix(Term.Select(_, Term.Name(m)), Term.Name("=="), _, List(Lit.Int(0)))
        if m == "size" || m == "length" =>
        issue(
          s"Use .isEmpty instead of .$m == 0",
          t.pos,
          file,
          suggestion = Some("Replace with .isEmpty for better performance")
        )
      case t @ Term.ApplyInfix(Term.Select(_, Term.Name(m)), Term.Name(">"), _, List(Lit.Int(0)))
        if m == "size" || m == "length" =>
        issue(
          s"Use .nonEmpty instead of .$m > 0",
          t.pos,
          file,
          suggestion = Some("Replace with .nonEmpty for better performance")
        )
    }
  }
}

/**
 * Rule: Avoid multiple traversals
 */
object MultipleTraversalsRule extends Rule {
  val id = "P002"
  val name = "multiple-traversals"
  val description = "Avoid multiple traversals of the same collection"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "Multiple traversals (e.g., map followed by filter) can be combined using foldLeft or a single collect for better performance."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect pattern: collection.filter(...).map(...)
      case t @ Term.Apply(
            Term.Select(
              Term.Apply(Term.Select(_, Term.Name("filter")), _),
              Term.Name("map")
            ), _) =>
        issue(
          "Consider using .collect instead of .filter followed by .map to avoid multiple traversals",
          t.pos,
          file,
          suggestion = Some("Use .collect { case x if condition => transformation }")
        )
      // Detect pattern: collection.map(...).flatten
      case t @ Term.Select(
            Term.Apply(Term.Select(_, Term.Name("map")), _),
            Term.Name("flatten")) =>
        issue(
          "Use .flatMap instead of .map followed by .flatten",
          t.pos,
          file,
          suggestion = Some("Replace .map(...).flatten with .flatMap(...)")
        )
    }
  }
}

/**
 * Rule: Use view for lazy evaluation
 */
object UseViewRule extends Rule {
  val id = "P003"
  val name = "use-view"
  val description = "Consider using view for lazy evaluation on large collections"
  val category = Category.Performance
  val severity = Severity.Hint
  override val explanation = "When chaining multiple collection operations, using .view can avoid creating intermediate collections."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect chained operations without view
      case apply @ Term.Apply(
            Term.Select(
              Term.Apply(Term.Select(_, Term.Name(op1)), _),
              Term.Name(op2)
            ), _)
          if isCollectionOp(op1) && isCollectionOp(op2) =>
        issue(
          "Consider using .view for lazy evaluation when chaining collection operations",
          apply.pos,
          file,
          suggestion = Some("Add .view before the chain: collection.view.map(...).filter(...).toList")
        )
    }
  }

  private val collectionOps = Set("map", "filter", "flatMap", "collect", "drop", "take", "slice")

  private def isCollectionOp(name: String): Boolean = collectionOps.contains(name)
}

/**
 * Rule: Inefficient string concatenation in loop
 */
object StringConcatInLoopRule extends Rule {
  val id = "P004"
  val name = "string-concat-loop"
  val description = "Avoid string concatenation in loops; use StringBuilder"
  val category = Category.Performance
  val severity = Severity.Warning
  override val explanation = "String concatenation creates new String objects. In loops, use StringBuilder or mkString for better performance."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect += with string in loop context
      case t @ Term.For(_, Term.Block(stats)) =>
        findStringConcatInStats(stats, t.pos, file)
      case t @ Term.While(_, Term.Block(stats)) =>
        findStringConcatInStats(stats, t.pos, file)
    }.flatten
  }

  private def findStringConcatInStats(stats: List[Stat], pos: Position, file: String): Seq[LintIssue] = {
    stats.collect {
      case Term.ApplyInfix(_, Term.Name("+="), _, List(_: Lit.String)) =>
        issue(
          "String concatenation in loop; consider using StringBuilder",
          pos,
          file,
          suggestion = Some("Use StringBuilder.append() or collection.mkString()")
        )
    }
  }
}

/**
 * Rule: Avoid explicit foreach with index access
 */
object AvoidExplicitForeachWithIndexRule extends Rule {
  val id = "P005"
  val name = "avoid-indexed-foreach"
  val description = "Avoid using indices for collection access in foreach"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "Using indices in foreach is less efficient and harder to read. Use zipWithIndex or indices method instead."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: for (i <- 0 until list.size) ... list(i)
      case f @ Term.For(List(Enumerator.Generator(_, Term.ApplyInfix(Lit.Int(0), Term.Name("until"), _, _))), _) =>
        issue(
          "Consider using .zipWithIndex or .indices instead of manual index iteration",
          f.pos,
          file,
          suggestion = Some("Use: for ((elem, idx) <- collection.zipWithIndex) { ... }")
        )
    }
  }
}

/**
 * Rule: Prefer specific collection types
 */
object PreferSpecificCollectionRule extends Rule {
  val id = "P006"
  val name = "prefer-specific-collection"
  val description = "Use specific collection types for better performance"
  val category = Category.Performance
  val severity = Severity.Hint
  override val explanation = "Using specific collection types (e.g., Vector for indexed access, List for head operations) improves performance."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect List with many indexed accesses
      case d: Defn.Def =>
        val body = d.body.syntax
        if (body.contains("List") && body.matches(".*\\(\\d+\\).*")) {
          Seq(issue(
            "If using indexed access frequently, consider using Vector instead of List",
            d.pos,
            file,
            suggestion = Some("Vector has O(1) indexed access, List has O(n)")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * Rule: Avoid regex compilation in loops
 */
object RegexInLoopRule extends Rule {
  val id = "P007"
  val name = "regex-in-loop"
  val description = "Avoid compiling regex patterns inside loops"
  val category = Category.Performance
  val severity = Severity.Warning
  override val explanation = "Compiling regex patterns is expensive. Compile once outside the loop and reuse the Pattern object."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.For(_, body) =>
        findRegexInTree(body, t.pos, file)
      case t @ Term.While(_, body) =>
        findRegexInTree(body, t.pos, file)
    }.flatten
  }

  private def findRegexInTree(tree: Tree, pos: Position, file: String): Seq[LintIssue] = {
    tree.collect {
      case Term.Select(Lit.String(_), Term.Name("r")) =>
        issue(
          "Regex pattern compiled inside loop; move compilation outside",
          pos,
          file,
          suggestion = Some("Define the regex as a val outside the loop: val pattern = \"...\".r")
        )
      case Term.Apply(Term.Select(Term.Name("Pattern"), Term.Name("compile")), _) =>
        issue(
          "Pattern.compile inside loop; move compilation outside",
          pos,
          file,
          suggestion = Some("Compile the pattern once outside the loop")
        )
    }
  }
}

/**
 * Rule: Use exists/forall instead of find/filter for Boolean result
 */
object UseExistsForAllRule extends Rule {
  val id = "P008"
  val name = "use-exists-forall"
  val description = "Use exists/forall instead of find/filter for Boolean checks"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "exists and forall can short-circuit and return early, while find and filter traverse the whole collection."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: collection.find(...).isDefined
      case t @ Term.Select(Term.Apply(Term.Select(_, Term.Name("find")), _), Term.Name("isDefined")) =>
        issue(
          "Use .exists instead of .find(...).isDefined",
          t.pos,
          file,
          suggestion = Some("Replace with .exists(predicate)")
        )
      // Detect: collection.find(...).nonEmpty
      case t @ Term.Select(Term.Apply(Term.Select(_, Term.Name("find")), _), Term.Name("nonEmpty")) =>
        issue(
          "Use .exists instead of .find(...).nonEmpty",
          t.pos,
          file,
          suggestion = Some("Replace with .exists(predicate)")
        )
      // Detect: collection.filter(...).isEmpty
      case t @ Term.Select(Term.Apply(Term.Select(_, Term.Name("filter")), _), Term.Name("isEmpty")) =>
        issue(
          "Use !.exists or .forall with negated predicate instead of .filter(...).isEmpty",
          t.pos,
          file,
          suggestion = Some("Replace with !collection.exists(predicate)")
        )
    }
  }
}

/**
 * Rule: Avoid creating unnecessary objects
 */
object UnnecessaryObjectCreationRule extends Rule {
  val id = "P009"
  val name = "unnecessary-object-creation"
  val description = "Avoid creating unnecessary intermediate objects"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "Creating intermediate objects can increase GC pressure. Use more direct methods when available."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: Some(x).getOrElse(y)
      case t @ Term.Apply(Term.Select(Term.Apply(Term.Name("Some"), _), Term.Name("getOrElse")), _) =>
        issue(
          "Creating Some just to call getOrElse is wasteful",
          t.pos,
          file,
          suggestion = Some("Use the value directly if you know it's non-null, or use Option(x).getOrElse(y)")
        )
      // Detect: List(x).head
      case t @ Term.Select(Term.Apply(Term.Name("List"), List(_)), Term.Name("head")) =>
        issue(
          "Creating a single-element List just to call head is wasteful",
          t.pos,
          file,
          suggestion = Some("Use the value directly")
        )
    }
  }
}

/**
 * Rule: Use hashCode efficiently
 */
object HashCodeEfficiencyRule extends Rule {
  val id = "P010"
  val name = "hashcode-efficiency"
  val description = "Ensure hashCode is implemented efficiently"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "hashCode should use only immutable fields and be computed efficiently for hash-based collections."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case cls: Defn.Class =>
        val hasHashCode = cls.templ.stats.exists {
          case d: Defn.Def if d.name.value == "hashCode" => true
          case _ => false
        }
        val hasVar = cls.ctor.paramss.flatten.exists { param =>
          param.mods.exists {
            case Mod.VarParam() => true
            case _ => false
          }
        }
        if (hasHashCode && hasVar) {
          Seq(issue(
            "hashCode uses var fields; this can cause issues with hash-based collections",
            cls.pos,
            file,
            suggestion = Some("Ensure hashCode only uses immutable fields")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * Rule: Large collection literals
 */
object LargeCollectionLiteralRule extends Rule {
  val id = "P011"
  val name = "large-collection-literal"
  val description = "Large collection literals should use builders for efficiency"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "Creating large collections with literal syntax (List(a,b,c,...)) " +
    "creates many intermediate lists. For collections > 10 elements, consider using a builder."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Apply(Term.Name(collName), args)
        if Set("List", "Seq", "Vector", "Set").contains(collName) && args.size > 20 =>
        issue(
          s"Large $collName literal with ${args.size} elements; consider using a builder",
          t.pos,
          file,
          suggestion = Some(s"Use ${collName}.newBuilder ++= elements for large collections")
        )
    }
  }
}

/**
 * Rule: Inefficient contains check
 */
object InefficientContainsRule extends Rule {
  val id = "P012"
  val name = "inefficient-contains"
  val description = "Use Set for frequent contains checks"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "List.contains is O(n) while Set.contains is O(1). " +
    "If you're checking contains frequently, convert to Set first."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    val containsCallsOnSameVar = scala.collection.mutable.Map[String, Int]()

    source.traverse {
      case t @ Term.Apply(Term.Select(Term.Name(varName), Term.Name("contains")), _) =>
        containsCallsOnSameVar(varName) = containsCallsOnSameVar.getOrElse(varName, 0) + 1
        if (containsCallsOnSameVar(varName) >= 3) {
          // Only report once per variable
          if (containsCallsOnSameVar(varName) == 3) {
            issues += issue(
              s"Multiple contains calls on '$varName'; consider converting to Set if it's a List",
              t.pos,
              file,
              suggestion = Some("Use val set = list.toSet for O(1) contains checks")
            )
          }
        }
      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule: Inefficient sorting
 */
object InefficientSortingRule extends Rule {
  val id = "P013"
  val name = "inefficient-sorting"
  val description = "Use sortBy instead of sortWith for simple key extraction"
  val category = Category.Performance
  val severity = Severity.Hint
  override val explanation = "sortBy is more efficient than sortWith for simple key extraction " +
    "because it only extracts the key once per element."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect sortWith that could be sortBy
      case t @ Term.Apply(
            Term.Select(_, Term.Name("sortWith")),
            List(Term.Function(_, Term.ApplyInfix(
              Term.Select(Term.Name(a1), Term.Name(field1)),
              Term.Name(op),
              _,
              List(Term.Select(Term.Name(a2), Term.Name(field2)))
            ))))
        if (op == "<" || op == ">") && field1 == field2 =>
        issue(
          s"sortWith with simple comparison can be replaced with sortBy(_.$field1)",
          t.pos,
          file,
          suggestion = Some(s"Use .sortBy(_.$field1) for better performance")
        )
    }
  }
}

/**
 * Rule: Avoid groupBy followed by mapValues
 */
object GroupByMapValuesRule extends Rule {
  val id = "P014"
  val name = "groupby-mapvalues"
  val description = "Consider using groupMapReduce instead of groupBy + mapValues"
  val category = Category.Performance
  val severity = Severity.Hint
  override val explanation = "Scala 2.13+ provides groupMapReduce which combines groupBy, map, and reduce " +
    "in a single pass for better performance."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect groupBy followed by mapValues
      case t @ Term.Apply(
            Term.Select(
              Term.Apply(Term.Select(_, Term.Name("groupBy")), _),
              Term.Name("mapValues")
            ), _) =>
        issue(
          "groupBy + mapValues can be replaced with groupMapReduce (Scala 2.13+)",
          t.pos,
          file,
          suggestion = Some("Use collection.groupMapReduce(key)(value)(reduce) for single-pass operation")
        )
      // Also detect view.mapValues pattern
      case t @ Term.Apply(
            Term.Select(
              Term.Select(
                Term.Apply(Term.Select(_, Term.Name("groupBy")), _),
                Term.Name("view")
              ),
              Term.Name("mapValues")
            ), _) =>
        issue(
          "groupBy.view.mapValues can be replaced with groupMapReduce (Scala 2.13+)",
          t.pos,
          file,
          suggestion = Some("Use collection.groupMapReduce(key)(value)(reduce) for better performance")
        )
    }
  }
}

/**
 * Rule: Avoid toList on Range
 */
object RangeToListRule extends Rule {
  val id = "P015"
  val name = "range-to-list"
  val description = "Avoid converting Range to List unnecessarily"
  val category = Category.Performance
  val severity = Severity.Info
  override val explanation = "Range is a lazy, memory-efficient representation. " +
    "Converting to List materializes all elements. Use Range directly when possible."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect (1 to n).toList or (1 until n).toList
      case t @ Term.Select(
            Term.ApplyInfix(_, Term.Name(rangeOp), _, _),
            Term.Name(toOp))
        if Set("to", "until").contains(rangeOp) && Set("toList", "toSeq", "toVector").contains(toOp) =>
        issue(
          s"Converting Range to ${toOp.drop(2)} materializes all elements",
          t.pos,
          file,
          suggestion = Some("Use Range directly if possible; it's lazy and memory-efficient")
        )
    }
  }
}

/**
 * All performance rules
 */
object PerformanceRules {
  val all: Seq[Rule] = Seq(
    UseSizeEqualsZeroRule,
    MultipleTraversalsRule,
    UseViewRule,
    StringConcatInLoopRule,
    AvoidExplicitForeachWithIndexRule,
    PreferSpecificCollectionRule,
    RegexInLoopRule,
    UseExistsForAllRule,
    UnnecessaryObjectCreationRule,
    HashCodeEfficiencyRule,
    LargeCollectionLiteralRule,
    InefficientContainsRule,
    InefficientSortingRule,
    GroupByMapValuesRule,
    RangeToListRule
  )
}
