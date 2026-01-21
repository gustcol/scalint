package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Delta Lake specific rules for detecting anti-patterns and best practices
 */

/**
 * Rule DELTA001: MERGE without specific conditions
 * Overly broad MERGE conditions can cause performance issues
 */
object DeltaMergeConditionRule extends Rule {
  val id = "DELTA001"
  val name = "merge-condition"
  val description = "MERGE operations should have specific match conditions"
  val category = Category.DeltaLake
  val severity = Severity.Warning
  override val explanation = "Delta Lake MERGE operations without specific conditions can cause full table scans " +
    "and excessive file rewrites. Always use specific match conditions and consider partition pruning."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect merge().whenMatched().updateAll() pattern
      case t @ Term.Select(
            Term.Apply(Term.Select(_, Term.Name("whenMatched")), Nil),
            Term.Name("updateAll")) =>
        Seq(issue(
          "updateAll() updates all columns - consider updating only changed columns",
          t.pos,
          file,
          suggestion = Some("Use .update(Map(col -> value)) for specific columns to reduce I/O")
        ))

      // Detect merge without condition
      case t @ Term.Apply(
            Term.Select(_, Term.Name("merge")),
            List(_, Lit.String(condition))) if condition.trim.isEmpty =>
        Seq(issue(
          "MERGE with empty condition - this will match all rows",
          t.pos,
          file,
          suggestion = Some("Specify a join condition: .merge(target, \"source.id = target.id\")")
        ))

      // Detect whenNotMatched().insertAll()
      case t @ Term.Select(
            Term.Apply(Term.Select(_, Term.Name("whenNotMatched")), Nil),
            Term.Name("insertAll")) =>
        Seq(issue(
          "insertAll() inserts all columns from source - verify this is intentional",
          t.pos,
          file,
          suggestion = Some("Consider .insert(Map(...)) for explicit column mapping")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule DELTA002: VACUUM with short retention
 */
object DeltaVacuumRetentionRule extends Rule {
  val id = "DELTA002"
  val name = "vacuum-retention"
  val description = "VACUUM retention period should be at least 7 days"
  val category = Category.DeltaLake
  val severity = Severity.Error
  override val explanation = "VACUUM removes old data files permanently. Setting retention below 7 days risks " +
    "data loss if long-running queries or time-travel queries need older versions. Delta Lake's default " +
    "is 168 hours (7 days) for good reason."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      // Detect vacuum() with hours parameter
      case t @ Term.Apply(Term.Select(_, Term.Name("vacuum")), List(Lit.Int(hours)))
        if hours < 168 =>
        issues += issue(
          s"VACUUM with ${hours}h retention - minimum recommended is 168h (7 days)",
          t.pos,
          file,
          suggestion = Some("Use at least 168 hours: deltaTable.vacuum(168)")
        )

      case t @ Term.Apply(Term.Select(_, Term.Name("vacuum")), List(Lit.Double(hoursStr)))
        if hoursStr.toDouble < 168.0 =>
        issues += issue(
          s"VACUUM with ${hoursStr}h retention is dangerously low",
          t.pos,
          file,
          suggestion = Some("Increase to at least 168 hours for safety")
        )

      // Detect retentionDurationCheck disabled
      case t @ Term.Apply(
            Term.Select(_, Term.Name("set")),
            List(Lit.String(key), Lit.String("false")))
        if key.contains("retentionDurationCheck") =>
        issues += issue(
          "Disabling retentionDurationCheck allows dangerous VACUUM operations",
          t.pos,
          file,
          suggestion = Some("Keep this check enabled; increase retention instead")
        )

      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule DELTA003: Z-ORDER on low cardinality columns
 */
object DeltaZOrderCardinalityRule extends Rule {
  val id = "DELTA003"
  val name = "zorder-cardinality"
  val description = "Z-ORDER is most effective on high-cardinality columns"
  val category = Category.DeltaLake
  val severity = Severity.Info
  override val explanation = "Z-ORDER clustering works best on columns with high cardinality (many unique values) " +
    "that are frequently used in filters. Low-cardinality columns like boolean or status fields " +
    "benefit more from partitioning than Z-ORDER."

  // Known low-cardinality column patterns
  private val lowCardinalityPatterns = Set(
    "status", "state", "type", "flag", "is_", "has_", "enabled", "active",
    "gender", "boolean", "bool", "category", "region", "country"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect OPTIMIZE with ZORDER BY
      case t @ Term.Apply(
            Term.Select(_, Term.Name("executeZOrderBy")),
            args) =>
        args.flatMap {
          case Lit.String(col) if isLowCardinality(col) =>
            Seq(issue(
              s"Z-ORDER on '$col' may be ineffective - appears to be low cardinality",
              t.pos,
              file,
              suggestion = Some("Z-ORDER works best on high-cardinality columns like IDs or timestamps")
            ))
          case _ => Seq.empty
        }

      // Detect zorderBy in SQL string
      case t @ Lit.String(sql) if sql.toLowerCase.contains("zorder by") =>
        val colMatch = """zorder\s+by\s+(\w+)""".r.findFirstMatchIn(sql.toLowerCase)
        colMatch.flatMap { m =>
          val col = m.group(1)
          if (isLowCardinality(col)) {
            Some(issue(
              s"Z-ORDER BY $col in SQL - column appears to be low cardinality",
              t.pos,
              file,
              suggestion = Some("Consider partitioning by low-cardinality columns instead")
            ))
          } else None
        }.toSeq

      case _ => Seq.empty
    }.flatten
  }

  private def isLowCardinality(col: String): Boolean = {
    val lower = col.toLowerCase
    lowCardinalityPatterns.exists(pattern => lower.contains(pattern))
  }
}

/**
 * Rule DELTA004: Partition pruning opportunities
 */
object DeltaPartitionPruningRule extends Rule {
  val id = "DELTA004"
  val name = "partition-pruning"
  val description = "Ensure filters leverage partition columns for optimal performance"
  val category = Category.DeltaLake
  val severity = Severity.Info
  override val explanation = "Delta Lake partitioning allows skipping entire files during queries. " +
    "Filters on partition columns enable partition pruning, dramatically reducing I/O. " +
    "Always filter on partition columns when possible."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var hasPartitionBy = false
    var partitionColumns: Set[String] = Set.empty

    // First pass: find partition columns
    source.traverse {
      case Term.Apply(Term.Select(_, Term.Name("partitionBy")), args) =>
        hasPartitionBy = true
        args.foreach {
          case Lit.String(col) => partitionColumns += col.toLowerCase
          case Term.Name(col) => partitionColumns += col.toLowerCase
          case _ =>
        }
      case _ =>
    }

    // Second pass: check if filters use partition columns (simplified heuristic)
    if (hasPartitionBy && partitionColumns.nonEmpty) {
      source.traverse {
        // Detect filter() calls
        case t @ Term.Apply(Term.Select(_, Term.Name("filter")), List(Lit.String(condition))) =>
          val usesPartition = partitionColumns.exists(col => condition.toLowerCase.contains(col))
          if (!usesPartition) {
            issues += issue(
              s"Filter does not use partition columns (${partitionColumns.mkString(", ")})",
              t.pos,
              file,
              suggestion = Some("Add partition column filter to enable partition pruning")
            )
          }
        case _ =>
      }
    }

    issues.toSeq
  }
}

/**
 * Rule DELTA005: Delta table without schema evolution config
 */
object DeltaSchemaEvolutionRule extends Rule {
  val id = "DELTA005"
  val name = "schema-evolution"
  val description = "Consider enabling automatic schema evolution for evolving data sources"
  val category = Category.DeltaLake
  val severity = Severity.Hint
  override val explanation = "Delta Lake supports automatic schema evolution with mergeSchema option. " +
    "For data sources with evolving schemas, enabling this prevents job failures when new columns appear."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var hasDeltaWrite = false
    var hasMergeSchema = false

    source.traverse {
      case Term.Apply(
            Term.Select(_, Term.Name("format")),
            List(Lit.String("delta"))) =>
        hasDeltaWrite = true
      case Term.Apply(
            Term.Select(_, Term.Name("option")),
            List(Lit.String("mergeSchema"), _)) =>
        hasMergeSchema = true
      case Lit.String(s) if s.contains("mergeSchema") =>
        hasMergeSchema = true
      case _ =>
    }

    if (hasDeltaWrite && !hasMergeSchema) {
      issues += LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = "Delta write without mergeSchema option - schema changes will fail",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Add .option(\"mergeSchema\", \"true\") for schema evolution"),
        explanation = Some(explanation)
      )
    }

    issues.toSeq
  }
}

/**
 * Rule DELTA006: Overwrite mode without replaceWhere
 */
object DeltaOverwriteRule extends Rule {
  val id = "DELTA006"
  val name = "overwrite-mode"
  val description = "Use replaceWhere for targeted overwrites instead of full table overwrite"
  val category = Category.DeltaLake
  val severity = Severity.Warning
  override val explanation = "Full table overwrites (mode='overwrite') replace all data and create new files. " +
    "For large tables, use replaceWhere to overwrite only specific partitions, reducing time and storage churn."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var hasOverwriteMode = false
    var hasReplaceWhere = false
    var hasDeltaFormat = false

    source.traverse {
      case Term.Apply(
            Term.Select(_, Term.Name("mode")),
            List(Lit.String("overwrite"))) =>
        hasOverwriteMode = true
      case Term.Apply(
            Term.Select(_, Term.Name("option")),
            List(Lit.String("replaceWhere"), _)) =>
        hasReplaceWhere = true
      case Term.Apply(
            Term.Select(_, Term.Name("format")),
            List(Lit.String("delta"))) =>
        hasDeltaFormat = true
      case _ =>
    }

    if (hasDeltaFormat && hasOverwriteMode && !hasReplaceWhere) {
      issues += LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = "Full Delta table overwrite detected - consider replaceWhere for partitioned tables",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Use .option(\"replaceWhere\", \"date = '2024-01-01'\") for targeted overwrite"),
        explanation = Some(explanation)
      )
    }

    issues.toSeq
  }
}

/**
 * All Delta Lake rules
 */
object DeltaLakeRules {
  val all: Seq[Rule] = Seq(
    DeltaMergeConditionRule,
    DeltaVacuumRetentionRule,
    DeltaZOrderCardinalityRule,
    DeltaPartitionPruningRule,
    DeltaSchemaEvolutionRule,
    DeltaOverwriteRule
  )
}
