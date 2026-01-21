package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Effect System rules for Cats Effect and ZIO anti-patterns
 */

/**
 * Rule EFF001: Unsafe run in production code
 */
object UnsafeRunRule extends Rule {
  val id = "EFF001"
  val name = "unsafe-run"
  val description = "Avoid unsafeRunSync/unsafeRunAsync in production code"
  val category = Category.EffectSystem
  val severity = Severity.Warning
  override val explanation = "unsafeRunSync blocks the thread and can cause deadlocks. " +
    "unsafeRunAsync loses the effect's guarantees. Use proper effect composition instead " +
    "and only call unsafe* at the edge of your application (main method)."

  private val unsafeMethods = Set(
    "unsafeRunSync", "unsafeRunTimed", "unsafeRunAsync", "unsafeRunAndForget",
    "unsafeToFuture", "unsafeRunAsyncAndForget", "unsafePerformIO", "unsafeRun"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Skip test files and main entry points
    if (file.contains("test") || file.contains("Test") || file.contains("Main") || file.contains("App")) {
      return Seq.empty
    }

    source.collect {
      case t @ Term.Select(_, Term.Name(method)) if unsafeMethods.contains(method) =>
        Seq(issue(
          s"$method breaks effect safety - should only be used at application edge",
          t.pos,
          file,
          suggestion = Some("Compose effects with flatMap/map; call unsafe* only in main")
        ))
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule EFF002: Blocking operations in IO without proper handling
 */
object BlockingInEffectRule extends Rule {
  val id = "EFF002"
  val name = "blocking-in-effect"
  val description = "Blocking operations should use IO.blocking or ZIO.attemptBlocking"
  val category = Category.EffectSystem
  val severity = Severity.Warning
  override val explanation = "Blocking operations like Thread.sleep, file I/O, or JDBC calls can starve " +
    "the compute thread pool. Wrap them in IO.blocking (Cats Effect) or ZIO.attemptBlocking (ZIO) " +
    "to run on a dedicated blocking thread pool."

  private val blockingPatterns = Set(
    "Thread.sleep", "wait", "join", "readLine", "readAllBytes",
    "getConnection", "executeQuery", "executeUpdate"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var inBlockingContext = false

    source.traverse {
      // Check for proper blocking context
      case Term.Apply(Term.Select(_, Term.Name("blocking")), _) =>
        inBlockingContext = true
      case Term.Apply(Term.Select(_, Term.Name("attemptBlocking")), _) =>
        inBlockingContext = true
      case Term.Apply(Term.Select(_, Term.Name("interruptible")), _) =>
        inBlockingContext = true

      // Detect Thread.sleep
      case t @ Term.Apply(Term.Select(Term.Name("Thread"), Term.Name("sleep")), _) if !inBlockingContext =>
        issues += issue(
          "Thread.sleep in effect - use IO.sleep or ZIO.sleep instead",
          t.pos,
          file,
          suggestion = Some("Use IO.sleep(duration) or ZIO.sleep(duration) for non-blocking delay")
        )

      // Detect blocking JDBC calls
      case t @ Term.Apply(Term.Select(_, Term.Name("getConnection")), _) if !inBlockingContext =>
        issues += issue(
          "JDBC getConnection is blocking - wrap in IO.blocking or ZIO.attemptBlocking",
          t.pos,
          file,
          suggestion = Some("Use IO.blocking(getConnection(...)) or a proper connection pool")
        )

      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule EFF003: Future in effect-based code
 */
object FutureInEffectRule extends Rule {
  val id = "EFF003"
  val name = "future-in-effect"
  val description = "Avoid mixing Future with IO/ZIO - use proper conversion"
  val category = Category.EffectSystem
  val severity = Severity.Info
  override val explanation = "Mixing Future with effect types leads to dual runtimes and lost error handling. " +
    "Use IO.fromFuture, ZIO.fromFuture, or better yet, replace Future-based code with pure effects."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    var hasIO = false
    var hasZIO = false
    val futureUsages = scala.collection.mutable.ArrayBuffer[Position]()

    source.traverse {
      // Check for effect type imports/usage
      case Importer(Term.Select(Term.Name("cats"), Term.Name("effect")), _) => hasIO = true
      case Importer(Term.Name("zio"), _) => hasZIO = true
      case Type.Name("IO") => hasIO = true
      case Type.Name("ZIO") => hasZIO = true
      case Type.Name("Task") => hasZIO = true

      // Collect Future usages
      case t @ Term.Apply(Term.Select(Term.Name("Future"), _), _) =>
        futureUsages += t.pos
      case t @ Type.Apply(Type.Name("Future"), _) =>
        futureUsages += t.pos

      case _ =>
    }

    if ((hasIO || hasZIO) && futureUsages.nonEmpty) {
      futureUsages.map { pos =>
        issue(
          "Future mixed with effect types - consider using pure effects",
          pos,
          file,
          suggestion = Some("Use IO.fromFuture or ZIO.fromFuture, or replace with pure effect")
        )
      }.toSeq
    } else {
      Seq.empty
    }
  }
}

/**
 * Rule EFF004: Catching Throwable/Exception in effect code
 */
object CatchThrowableInEffectRule extends Rule {
  val id = "EFF004"
  val name = "catch-throwable-effect"
  val description = "Use effect error handling instead of try-catch"
  val category = Category.EffectSystem
  val severity = Severity.Warning
  override val explanation = "In effect-based code, use IO.attempt, handleError, or ZIO typed errors " +
    "instead of try-catch blocks. This maintains referential transparency and composability."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    var hasEffectImport = false

    source.traverse {
      case Importer(Term.Select(Term.Name("cats"), Term.Name("effect")), _) => hasEffectImport = true
      case Importer(Term.Name("zio"), _) => hasEffectImport = true
      case _ =>
    }

    if (!hasEffectImport) {
      return Seq.empty
    }

    source.collect {
      case t @ Term.Try(_, catchCases, _) if catchCases.nonEmpty =>
        Seq(issue(
          "try-catch in effect-based code - use IO.attempt or handleError",
          t.pos,
          file,
          suggestion = Some("Use .attempt.flatMap { case Left(e) => ... case Right(a) => ... }")
        ))
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule EFF005: Discarded effect
 */
object DiscardedEffectRule extends Rule {
  val id = "EFF005"
  val name = "discarded-effect"
  val description = "Effects should not be discarded - they represent computations"
  val category = Category.EffectSystem
  val severity = Severity.Error
  override val explanation = "Discarding an effect means the computation never runs. " +
    "Use *> or >> to sequence effects, or use _ <- in for-comprehensions."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect statements that return IO/ZIO but result is discarded
      case t @ Term.Block(stats) =>
        stats.init.flatMap {
          case Term.Apply(Term.Select(_, Term.Name("map")), _) =>
            Seq(issue(
              "Effect returned by map is discarded - computation won't run",
              t.pos,
              file,
              suggestion = Some("Use flatMap or *> to sequence effects")
            ))
          case Term.Apply(Term.Select(_, Term.Name("flatMap")), _) =>
            Seq(issue(
              "Effect returned by flatMap is discarded",
              t.pos,
              file,
              suggestion = Some("Include effect in the computation chain")
            ))
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule EFF006: Resource leak without bracket/Resource
 */
object ResourceLeakEffectRule extends Rule {
  val id = "EFF006"
  val name = "resource-leak-effect"
  val description = "Use Resource or bracket for safe resource management"
  val category = Category.EffectSystem
  val severity = Severity.Warning
  override val explanation = "Opening resources (files, connections, streams) without proper cleanup " +
    "leads to resource leaks. Use cats.effect.Resource or ZIO Scope for automatic cleanup."

  private val resourceOpenMethods = Set(
    "openInputStream", "openOutputStream", "getConnection", "open", "newInputStream",
    "newOutputStream", "newBufferedReader", "newBufferedWriter"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var hasResourceImport = false

    source.traverse {
      case Importer(_, importees) =>
        importees.foreach {
          case Importee.Name(Name("Resource")) => hasResourceImport = true
          case Importee.Name(Name("Scope")) => hasResourceImport = true
          case Importee.Name(Name("bracket")) => hasResourceImport = true
          case _ =>
        }
      case _ =>
    }

    if (hasResourceImport) {
      return Seq.empty // Already using proper resource management
    }

    source.traverse {
      case t @ Term.Apply(Term.Select(_, Term.Name(method)), _) if resourceOpenMethods.contains(method) =>
        issues += issue(
          s"$method opens a resource - use Resource.make or bracket for safe cleanup",
          t.pos,
          file,
          suggestion = Some("Wrap with Resource.make(acquire)(release) or use bracket")
        )
      case _ =>
    }

    issues.toSeq
  }
}

/**
 * Rule EFF007: Fiber without join or cancel
 */
object FiberLeakRule extends Rule {
  val id = "EFF007"
  val name = "fiber-leak"
  val description = "Forked fibers should be joined or managed"
  val category = Category.EffectSystem
  val severity = Severity.Warning
  override val explanation = "Fibers that are forked but never joined or canceled can leak resources " +
    "and continue running after their parent completes. Use Supervisor, background, or explicit join/cancel."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()
    var fiberVars = Set.empty[String]
    var joinedVars = Set.empty[String]

    source.traverse {
      // Track fiber creations
      case Term.Assign(Term.Name(name), Term.Apply(Term.Select(_, Term.Name("start")), _)) =>
        fiberVars += name
      case Defn.Val(_, List(Pat.Var(Term.Name(name))), _, Term.Apply(Term.Select(_, Term.Name("start")), _)) =>
        fiberVars += name
      case Defn.Val(_, List(Pat.Var(Term.Name(name))), _, Term.Apply(Term.Select(_, Term.Name("fork")), _)) =>
        fiberVars += name

      // Track joins/cancels
      case Term.Apply(Term.Select(Term.Name(name), Term.Name("join")), _) =>
        joinedVars += name
      case Term.Apply(Term.Select(Term.Name(name), Term.Name("cancel")), _) =>
        joinedVars += name

      case _ =>
    }

    val leakedFibers = fiberVars -- joinedVars
    if (leakedFibers.nonEmpty) {
      issues += LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = s"Fibers started but not joined: ${leakedFibers.mkString(", ")}",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Use .join to wait for completion or Supervisor for managed fibers"),
        explanation = Some(explanation)
      )
    }

    issues.toSeq
  }
}

/**
 * Rule EFF008: Nested flatMaps (callback hell)
 */
object NestedFlatMapRule extends Rule {
  val id = "EFF008"
  val name = "nested-flatmap"
  val description = "Deeply nested flatMaps reduce readability - use for-comprehension"
  val category = Category.EffectSystem
  val severity = Severity.Info
  override val explanation = "Deeply nested flatMap chains are hard to read and maintain. " +
    "For-comprehensions provide cleaner syntax: for { a <- fa; b <- fb(a) } yield result"

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    def countNestingDepth(term: Term, depth: Int = 0): Int = term match {
      case Term.Apply(Term.Select(inner, Term.Name("flatMap")), _) =>
        countNestingDepth(inner, depth + 1)
      case _ => depth
    }

    source.collect {
      case t @ Term.Apply(Term.Select(inner, Term.Name("flatMap")), _) =>
        val depth = countNestingDepth(t)
        if (depth >= 3) {
          Seq(issue(
            s"$depth levels of nested flatMap - consider for-comprehension",
            t.pos,
            file,
            suggestion = Some("Use: for { a <- fa; b <- fb; c <- fc } yield result")
          ))
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * All Effect System rules
 */
object EffectSystemRules {
  val all: Seq[Rule] = Seq(
    UnsafeRunRule,
    BlockingInEffectRule,
    FutureInEffectRule,
    CatchThrowableInEffectRule,
    DiscardedEffectRule,
    ResourceLeakEffectRule,
    FiberLeakRule,
    NestedFlatMapRule
  )
}
