package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Extended Spark rules for advanced anti-pattern detection
 */

/**
 * Rule SPK008: Detect potential join skew without mitigation
 * Large joins without broadcast hints or salting can cause data skew
 */
object JoinSkewRule extends Rule {
  val id = "SPK008"
  val name = "join-skew-detection"
  val description = "Detect joins that may suffer from data skew"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "Joins on skewed keys cause some tasks to process much more data than others, " +
    "leading to job slowdowns. Consider using broadcast joins for small tables, salting for skewed keys, " +
    "or Adaptive Query Execution (AQE) in Spark 3.0+."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var hasBroadcastHint = false
    var hasAqeConfig = false

    // Check for broadcast hints or AQE configuration
    source.traverse {
      case Term.Apply(Term.Name("broadcast"), _) => hasBroadcastHint = true
      case Term.Apply(Term.Select(_, Term.Name("broadcast")), _) => hasBroadcastHint = true
      case Lit.String(s) if s.contains("adaptive") && s.contains("enabled") => hasAqeConfig = true
      case _ =>
    }

    source.traverse {
      // Detect regular joins without broadcast
      case t @ Term.Apply(Term.Select(_, Term.Name("join")), args) if !hasBroadcastHint =>
        // Check if it's a simple join without any hints
        val hasHint = args.exists {
          case Term.Apply(Term.Name("broadcast"), _) => true
          case _ => false
        }
        if (!hasHint && !hasAqeConfig) {
          issues += issue(
            "Join without broadcast hint - may suffer from data skew on large datasets",
            t.pos,
            file,
            suggestion = Some("Consider: df1.join(broadcast(df2), ...) for small tables, or enable AQE")
          )
        }

      // Detect leftOuterJoin/rightOuterJoin (often skewed)
      case t @ Term.Apply(Term.Select(_, Term.Name(joinType)), _)
        if Set("leftOuterJoin", "rightOuterJoin", "fullOuterJoin").contains(joinType) =>
        issues += issue(
          s"$joinType detected - outer joins are particularly susceptible to skew",
          t.pos,
          file,
          suggestion = Some("Monitor partition sizes; consider salting skewed keys")
        )

      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule SPK009: Streaming without checkpoint location
 */
object StreamingCheckpointRule extends Rule {
  val id = "SPK009"
  val name = "streaming-checkpoint"
  val description = "Structured Streaming should have checkpoint location configured"
  val category = Category.Spark
  val severity = Severity.Error
  override val explanation = "Structured Streaming requires checkpointing for fault tolerance and exactly-once " +
    "semantics. Without checkpoints, the stream cannot recover from failures and may reprocess or lose data."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var hasWriteStream = false
    var hasCheckpoint = false

    source.traverse {
      case Term.Select(_, Term.Name("writeStream")) => hasWriteStream = true
      case Term.Apply(Term.Select(_, Term.Name("option")), List(Lit.String("checkpointLocation"), _)) =>
        hasCheckpoint = true
      case Term.Apply(Term.Select(_, Term.Name("checkpointLocation")), _) =>
        hasCheckpoint = true
      case Lit.String(s) if s.contains("checkpointLocation") => hasCheckpoint = true
      case _ =>
    }

    if (hasWriteStream && !hasCheckpoint) {
      source.traverse {
        case t @ Term.Select(_, Term.Name("writeStream")) =>
          issues += issue(
            "Structured Streaming without checkpointLocation - cannot recover from failures",
            t.pos,
            file,
            suggestion = Some("Add .option(\"checkpointLocation\", \"/path/to/checkpoint\")")
          )
        case _ =>
      }
    }

    issues.toSeq
  }
}

/**
 * Rule SPK010: Schema inference in production
 */
object SchemaInferenceRule extends Rule {
  val id = "SPK010"
  val name = "schema-inference"
  val description = "Avoid schema inference in production - define schema explicitly"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "Schema inference (inferSchema=true) reads the entire dataset twice - once for " +
    "inference, once for loading. It's also non-deterministic if data changes. Always define schemas explicitly " +
    "in production for performance and reliability."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect inferSchema option
      case t @ Term.Apply(
            Term.Select(_, Term.Name("option")),
            List(Lit.String("inferSchema"), Lit.Boolean(true))) =>
        Seq(issue(
          "inferSchema=true causes full data scan - define schema explicitly",
          t.pos,
          file,
          suggestion = Some("Use .schema(StructType(...)) or .schema(\"col1 INT, col2 STRING\")")
        ))

      // Detect spark.read.json without schema
      case t @ Term.Apply(
            Term.Select(
              Term.Select(_, Term.Name("read")),
              Term.Name(format)
            ), _) if Set("json", "csv").contains(format) =>
        Seq(issue(
          s"spark.read.$format() without explicit schema - will infer schema",
          t.pos,
          file,
          suggestion = Some(s"Add .schema(mySchema) before .$format()")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule SPK011: Coalesce to single partition
 */
object CoalesceOneRule extends Rule {
  val id = "SPK011"
  val name = "coalesce-one"
  val description = "coalesce(1) creates a single partition bottleneck"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "coalesce(1) forces all data through a single task, eliminating parallelism. " +
    "This is only acceptable for very small datasets or when a single output file is required. " +
    "Consider using repartition() with more partitions for better distribution."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Apply(Term.Select(_, Term.Name("coalesce")), List(Lit.Int(1))) =>
        Seq(issue(
          "coalesce(1) eliminates parallelism - all data processed by single task",
          t.pos,
          file,
          suggestion = Some("Only use for small data; consider .coalesce(n) where n > 1")
        ))

      case t @ Term.Apply(Term.Select(_, Term.Name("repartition")), List(Lit.Int(1))) =>
        Seq(issue(
          "repartition(1) causes full shuffle to single partition",
          t.pos,
          file,
          suggestion = Some("Use coalesce(1) if reducing partitions (avoids shuffle)")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule SPK012: Window functions without partition
 */
object WindowWithoutPartitionRule extends Rule {
  val id = "SPK012"
  val name = "window-without-partition"
  val description = "Window functions without partitionBy cause global shuffle"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "Window functions without partitionBy() process all data in a single partition, " +
    "causing a massive shuffle and potential OOM errors. Always partition window operations by a key column."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      // Detect Window.orderBy without partitionBy
      case t @ Term.Apply(
            Term.Select(Term.Name("Window"), Term.Name("orderBy")), _) =>
        issues += issue(
          "Window.orderBy() without partitionBy() - causes global shuffle",
          t.pos,
          file,
          suggestion = Some("Use Window.partitionBy(col).orderBy(col) to distribute work")
        )

      // Detect row_number/rank without partition
      case t @ Term.Apply(
            Term.Select(_, Term.Name(func)),
            List(Term.Apply(Term.Select(Term.Name("Window"), Term.Name("orderBy")), _)))
        if Set("row_number", "rank", "dense_rank", "lead", "lag").contains(func) =>
        issues += issue(
          s"$func() with Window.orderBy() only - all data in single partition",
          t.pos,
          file,
          suggestion = Some(s"Add partitionBy: $func().over(Window.partitionBy(key).orderBy(...))")
        )

      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule SPK013: Detect count() followed by collect()
 */
object CountThenCollectRule extends Rule {
  val id = "SPK013"
  val name = "count-then-collect"
  val description = "Calling count() then collect() processes data twice"
  val category = Category.Spark
  val severity = Severity.Info
  override val explanation = "If you need both count and data, collect first then get length from the result. " +
    "Calling count() then collect() causes two separate job submissions."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    var hasCount = false
    var hasCollect = false
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      case Term.Apply(Term.Select(qual, Term.Name("count")), Nil) =>
        hasCount = true
      case Term.Apply(Term.Select(qual, Term.Name("collect")), Nil) =>
        hasCollect = true
      case _ =>
    }

    if (hasCount && hasCollect) {
      issues += LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = "Both count() and collect() detected - consider collecting once if you need both",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("val data = df.collect(); val count = data.length"),
        explanation = Some(explanation)
      )
    }

    issues.toSeq
  }
}

/**
 * Rule SPK014: Detect show() in production code
 */
object ShowInProductionRule extends Rule {
  val id = "SPK014"
  val name = "show-in-production"
  val description = "show() is for debugging - avoid in production code"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "df.show() collects data to the driver for display. It's useful for debugging " +
    "but should not be in production code as it can cause memory issues with large datasets."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Skip test files
    if (file.contains("test") || file.contains("Test") || file.contains("Spec")) {
      return Seq.empty
    }

    source.collect {
      case t @ Term.Apply(Term.Select(_, Term.Name("show")), _) =>
        Seq(issue(
          "show() is for debugging - remove from production code",
          t.pos,
          file,
          suggestion = Some("Use logging or write to storage instead")
        ))
      case t @ Term.Select(_, Term.Name("show")) =>
        Seq(issue(
          "show() detected - this collects data to driver",
          t.pos,
          file,
          suggestion = Some("Remove show() or guard with if(isDebug)")
        ))
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule SPK015: Detect toPandas() with large datasets
 */
object ToPandasRule extends Rule {
  val id = "SPK015"
  val name = "to-pandas-warning"
  val description = "toPandas() collects all data to driver memory"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "toPandas() converts a Spark DataFrame to a Pandas DataFrame by collecting all " +
    "data to the driver. This can cause OOM errors with large datasets. Consider using Spark operations " +
    "or sampling data first."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Select(_, Term.Name("toPandas")) =>
        Seq(issue(
          "toPandas() collects all data to driver - ensure dataset is small",
          t.pos,
          file,
          suggestion = Some("Consider .limit(n).toPandas() or use Spark operations")
        ))
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Extended Spark Rules collection
 */
object SparkRulesExtended {
  val all: Seq[Rule] = Seq(
    JoinSkewRule,
    StreamingCheckpointRule,
    SchemaInferenceRule,
    CoalesceOneRule,
    WindowWithoutPartitionRule,
    CountThenCollectRule,
    ShowInProductionRule,
    ToPandasRule
  )
}
