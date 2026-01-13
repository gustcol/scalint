package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: DataFrame collect() in loop
 * Calling .collect() inside a loop sends all data to driver repeatedly
 */
object CollectInLoopRule extends Rule {
  val id = "SPK001"
  val name = "collect-in-loop"
  val description = "Avoid calling .collect() inside loops"
  val category = Category.Spark
  val severity = Severity.Error
  override val explanation = "Calling .collect() inside a loop repeatedly transfers all data to the driver, " +
    "bypassing Spark's distributed processing benefits and potentially causing OutOfMemoryError."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      // Detect collect() inside for loops
      case forLoop @ Term.For(_, body) =>
        body.traverse {
          case t @ Term.Apply(Term.Select(_, Term.Name("collect")), _) =>
            issues += issue(
              "Calling .collect() inside a loop transfers data to driver repeatedly",
              t.pos,
              file,
              suggestion = Some("Collect data once outside the loop, or use Spark operations like foreachPartition")
            )
          case _ =>
        }

      // Detect collect() inside while loops
      case whileLoop @ Term.While(_, body) =>
        body.traverse {
          case t @ Term.Apply(Term.Select(_, Term.Name("collect")), _) =>
            issues += issue(
              "Calling .collect() inside a while loop transfers data to driver repeatedly",
              t.pos,
              file,
              suggestion = Some("Restructure to avoid collecting inside loops")
            )
          case _ =>
        }

      // Detect collect() inside map/flatMap on collections
      case t @ Term.Apply(Term.Select(_, Term.Name(mapOp)), List(Term.Function(_, body)))
        if Set("map", "flatMap", "foreach").contains(mapOp) =>
        body.traverse {
          case collect @ Term.Apply(Term.Select(_, Term.Name("collect")), _) =>
            issues += issue(
              s"Calling .collect() inside $mapOp transfers data to driver on each iteration",
              collect.pos,
              file,
              suggestion = Some("Collect once before the iteration, or use Spark-native operations")
            )
          case _ =>
        }

      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule: Broadcast variable with mutable data
 */
object BroadcastMutableRule extends Rule {
  val id = "SPK002"
  val name = "broadcast-mutable"
  val description = "Avoid broadcasting mutable data structures"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "Broadcasting mutable collections (ArrayBuffer, HashMap, etc.) can cause " +
    "non-deterministic behavior due to serialization/deserialization across executors."

  private val mutableTypes = Set(
    "ArrayBuffer", "ListBuffer", "StringBuilder", "HashMap", "HashSet",
    "LinkedHashMap", "LinkedHashSet", "TreeMap", "TreeSet", "PriorityQueue",
    "Stack", "Queue", "MutableList", "mutable.Map", "mutable.Set", "mutable.Buffer"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      // Detect sc.broadcast with mutable types
      case t @ Term.Apply(Term.Select(_, Term.Name("broadcast")), args) =>
        args.foreach { arg =>
          arg.traverse {
            // New mutable collection
            case Term.New(Init(Type.Name(typeName), _, _)) if isMutableType(typeName) =>
              issues += issue(
                s"Broadcasting mutable type '$typeName' can cause non-deterministic behavior",
                t.pos,
                file,
                suggestion = Some("Use immutable collections: Map, Vector, List instead")
              )
            // mutable.Map/Set constructor
            case Term.Apply(Term.Select(Term.Name("mutable"), Term.Name(typeName)), _) =>
              issues += issue(
                s"Broadcasting mutable.$typeName can cause race conditions on executors",
                t.pos,
                file,
                suggestion = Some("Convert to immutable: .toMap, .toVector, .toList")
              )
            case _ =>
          }
        }
      case _ =>
    }

    issues.toSeq
  }

  private def isMutableType(name: String): Boolean = mutableTypes.exists(name.contains)
}

/**
 * Rule: RDD API when DataFrame would be better
 */
object RddVsDataFrameRule extends Rule {
  val id = "SPK003"
  val name = "prefer-dataframe"
  val description = "Consider using DataFrame API instead of RDD for structured data"
  val category = Category.Spark
  val severity = Severity.Info
  override val explanation = "DataFrame/Dataset API benefits from Catalyst optimizer and Tungsten execution, " +
    "often providing 10-100x better performance than equivalent RDD operations."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    var rddOperationCount = 0
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.traverse {
      // Count chained RDD operations
      case Term.Select(Term.Apply(Term.Select(_, Term.Name(op1)), _), Term.Name(op2))
        if isRddOperation(op1) && isRddOperation(op2) =>
        rddOperationCount += 1
        if (rddOperationCount >= 3) {
          // Only warn once per file when there are many RDD chains
        }

      // Detect textFile followed by map/split pattern (CSV parsing via RDD)
      case t @ Term.Apply(
            Term.Select(
              Term.Apply(Term.Select(_, Term.Name("textFile")), _),
              Term.Name("map")
            ), _) =>
        issues += issue(
          "Parsing files with RDD.textFile().map() - consider spark.read.csv() instead",
          t.pos,
          file,
          suggestion = Some("Use spark.read.csv() or spark.read.text() with DataFrame API")
        )

      case _ =>
    }

    // Add general warning if many RDD operations
    if (rddOperationCount >= 5) {
      issues += LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = Severity.Hint,
        message = s"Found $rddOperationCount chained RDD operations; consider DataFrame API for better performance",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("DataFrames use Catalyst optimizer for query planning"),
        explanation = Some(explanation)
      )
    }

    issues.toSeq
  }

  private val rddOperations = Set(
    "map", "flatMap", "filter", "reduce", "fold", "aggregate",
    "groupBy", "groupByKey", "reduceByKey", "combineByKey",
    "sortBy", "sortByKey", "join", "cogroup", "cartesian"
  )

  private def isRddOperation(name: String): Boolean = rddOperations.contains(name)
}

/**
 * Rule: UDF usage (prefer native Spark functions)
 */
object AvoidUdfRule extends Rule {
  val id = "SPK004"
  val name = "avoid-udf"
  val description = "Prefer native Spark functions over UDFs"
  val category = Category.Spark
  val severity = Severity.Warning
  override val explanation = "User Defined Functions (UDFs) prevent Catalyst optimization and require " +
    "serialization between JVM and Python/Scala. Native Spark functions are 10-100x faster."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect udf() registration
      case t @ Term.Apply(Term.Name("udf"), _) =>
        Seq(issue(
          "UDF detected - native Spark functions are significantly faster",
          t.pos,
          file,
          suggestion = Some("Check if equivalent exists in org.apache.spark.sql.functions._")
        ))

      // Detect spark.udf.register
      case t @ Term.Apply(Term.Select(Term.Select(_, Term.Name("udf")), Term.Name("register")), _) =>
        Seq(issue(
          "Registered UDF - consider using native Spark functions for better performance",
          t.pos,
          file,
          suggestion = Some("Use DataFrame operations or built-in functions when possible")
        ))

      // Detect Python UDF via F.udf
      case t @ Term.Apply(Term.Select(Term.Name("F"), Term.Name("udf")), _) =>
        Seq(issue(
          "PySpark UDF - these are particularly slow due to Python serialization",
          t.pos,
          file,
          suggestion = Some("Use pandas_udf for vectorized operations, or native functions")
        ))
    }.flatten
  }
}

/**
 * Rule: Shuffle operations without proper partitioning
 */
object ShuffleWarningRule extends Rule {
  val id = "SPK005"
  val name = "shuffle-warning"
  val description = "Potentially expensive shuffle operation detected"
  val category = Category.Spark
  val severity = Severity.Info
  override val explanation = "Operations like groupByKey, join without broadcast, and repartition cause " +
    "expensive data shuffles across the cluster. Consider broadcast joins for small tables."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect groupByKey (prefer reduceByKey)
      case t @ Term.Select(_, Term.Name("groupByKey")) =>
        Seq(issue(
          "groupByKey causes full shuffle; prefer reduceByKey or aggregateByKey",
          t.pos,
          file,
          suggestion = Some("reduceByKey combines values before shuffle, reducing data transfer")
        ))

      // Detect repartition (might be intentional but worth noting)
      case t @ Term.Apply(Term.Select(_, Term.Name("repartition")), args) =>
        Seq(issue(
          "repartition causes full shuffle of all data",
          t.pos,
          file,
          suggestion = Some("Consider coalesce() if reducing partitions, or repartition with partition key")
        ))

      // Detect distinct (can be expensive)
      case t @ Term.Select(_, Term.Name("distinct")) =>
        Seq(issue(
          "distinct() requires shuffle to compare all records",
          t.pos,
          file,
          suggestion = Some("Consider dropDuplicates() with specific columns if not all columns needed")
        ))

      // Detect crossJoin
      case t @ Term.Apply(Term.Select(_, Term.Name("crossJoin")), _) =>
        Seq(issue(
          "crossJoin creates cartesian product - O(n*m) rows",
          t.pos,
          file,
          suggestion = Some("Ensure this is intentional; result size can explode")
        ))
    }.flatten
  }
}

/**
 * Rule: Caching without unpersist
 */
object CacheWithoutUnpersistRule extends Rule {
  val id = "SPK006"
  val name = "cache-unpersist"
  val description = "Cached DataFrame/RDD should be unpersisted when no longer needed"
  val category = Category.Spark
  val severity = Severity.Info
  override val explanation = "Cached data consumes cluster memory. Forgetting to unpersist can lead to " +
    "memory pressure and job failures. Always unpersist when cached data is no longer needed."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    var cacheCount = 0
    var unpersistCount = 0

    source.traverse {
      case Term.Select(_, Term.Name("cache")) => cacheCount += 1
      case Term.Select(_, Term.Name("persist")) => cacheCount += 1
      case Term.Apply(Term.Select(_, Term.Name("unpersist")), _) => unpersistCount += 1
      case Term.Select(_, Term.Name("unpersist")) => unpersistCount += 1
      case _ =>
    }

    if (cacheCount > unpersistCount && cacheCount > 0) {
      Seq(LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = s"Found $cacheCount cache/persist calls but only $unpersistCount unpersist calls",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Call .unpersist() when cached data is no longer needed"),
        explanation = Some(explanation)
      ))
    } else Seq.empty
  }
}

/**
 * Rule: Spark SQL without proper error handling
 */
object SparkSqlInjectionRule extends Rule {
  val id = "SPK007"
  val name = "spark-sql-injection"
  val description = "Potential SQL injection in Spark SQL"
  val category = Category.Spark
  val severity = Severity.Error
  override val explanation = "String interpolation in Spark SQL queries can lead to SQL injection. " +
    "Use parameterized queries or DataFrame API instead."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect spark.sql with string interpolation
      case t @ Term.Apply(
            Term.Select(_, Term.Name("sql")),
            List(interp: Term.Interpolate)) if interp.args.nonEmpty =>
        Seq(issue(
          "String interpolation in Spark SQL - potential SQL injection risk",
          t.pos,
          file,
          suggestion = Some("Use DataFrame API or properly sanitize inputs")
        ))

      // Detect spark.sql with string concatenation
      case t @ Term.Apply(
            Term.Select(_, Term.Name("sql")),
            List(Term.ApplyInfix(_, Term.Name("+"), _, _))) =>
        Seq(issue(
          "String concatenation in Spark SQL - potential SQL injection risk",
          t.pos,
          file,
          suggestion = Some("Use DataFrame API for dynamic queries")
        ))
    }.flatten
  }
}

/**
 * All Spark rules
 */
object SparkRules {
  val all: Seq[Rule] = Seq(
    CollectInLoopRule,
    BroadcastMutableRule,
    RddVsDataFrameRule,
    AvoidUdfRule,
    ShuffleWarningRule,
    CacheWithoutUnpersistRule,
    SparkSqlInjectionRule
  )
}
