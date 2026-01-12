package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: Avoid using var in concurrent code
 */
object VarInConcurrentCodeRule extends Rule {
  val id = "C001"
  val name = "var-in-concurrent"
  val description = "Avoid using var in concurrent code without synchronization"
  val category = Category.Concurrency
  val severity = Severity.Warning
  override val explanation = "Mutable variables accessed from multiple threads without synchronization can cause race conditions."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Check if file has concurrent patterns
    val hasConcurrentImports = source.collect {
      case i: Import =>
        i.syntax.contains("concurrent") ||
        i.syntax.contains("akka") ||
        i.syntax.contains("Future")
    }.exists(identity)

    if (hasConcurrentImports) {
      source.collect {
        case d: Defn.Var =>
          val hasVolatile = d.mods.exists {
            case Mod.Annot(Init(Type.Name("volatile"), _, _)) => true
            case _ => false
          }
          if (!hasVolatile) {
            d.pats.map { pat =>
              issue(
                "Mutable variable in concurrent context without @volatile annotation",
                d.pos,
                file,
                suggestion = Some("Use @volatile, AtomicReference, or immutable data structures")
              )
            }
          } else Seq.empty
      }.flatten
    } else Seq.empty
  }
}

/**
 * Rule: Avoid blocking in Future
 */
object BlockingInFutureRule extends Rule {
  val id = "C002"
  val name = "blocking-in-future"
  val description = "Avoid blocking operations inside Future"
  val category = Category.Concurrency
  val severity = Severity.Warning
  override val explanation = "Blocking inside a Future can exhaust the thread pool. Use scala.concurrent.blocking or a separate ExecutionContext."

  private val blockingCalls = Set(
    "Thread.sleep", "await", "Await.result", "Await.ready",
    "readLine", "synchronized"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case Term.Apply(Term.Select(Term.Name("Future"), Term.Name("apply")), List(body)) =>
        findBlockingCalls(body)
      case Term.Apply(Term.Name("Future"), List(body)) =>
        findBlockingCalls(body)
    }.flatten
  }

  private def findBlockingCalls(tree: Tree): Seq[LintIssue] = {
    tree.collect {
      case t @ Term.Apply(Term.Select(Term.Name("Thread"), Term.Name("sleep")), _) =>
        LintIssue(
          ruleId = id,
          ruleName = name,
          category = category,
          severity = severity,
          message = "Thread.sleep blocks the Future's thread; use akka scheduler or wrap in blocking { }",
          position = SourcePosition.fromMeta(t.pos, ""),
          suggestion = Some("Use scala.concurrent.blocking { Thread.sleep(...) } or akka scheduler"),
          explanation = Some(explanation)
        )
      case t @ Term.Apply(Term.Select(Term.Name("Await"), Term.Name("result")), _) =>
        LintIssue(
          ruleId = id,
          ruleName = name,
          category = category,
          severity = severity,
          message = "Await.result blocks the thread; avoid inside Future",
          position = SourcePosition.fromMeta(t.pos, ""),
          suggestion = Some("Use flatMap/map to chain Futures instead of blocking"),
          explanation = Some(explanation)
        )
    }
  }
}

/**
 * Rule: Missing ExecutionContext
 */
object MissingExecutionContextRule extends Rule {
  val id = "C003"
  val name = "missing-execution-context"
  val description = "Future operations require an ExecutionContext"
  val category = Category.Concurrency
  val severity = Severity.Info
  override val explanation = "Using the global ExecutionContext can lead to thread starvation. Consider using a custom ExecutionContext for I/O operations."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val usesGlobalEc = source.collect {
      case i: Import if i.syntax.contains("ExecutionContext.Implicits.global") =>
        issue(
          "Using global ExecutionContext; consider a custom ExecutionContext for better control",
          i.pos,
          file,
          suggestion = Some("Define a custom ExecutionContext for I/O-bound operations")
        )
    }

    usesGlobalEc
  }
}

/**
 * Rule: Avoid synchronized on public methods
 */
object SynchronizedOnPublicRule extends Rule {
  val id = "C004"
  val name = "synchronized-on-public"
  val description = "Avoid using synchronized on public methods"
  val category = Category.Concurrency
  val severity = Severity.Info
  override val explanation = "Synchronized public methods expose the lock, allowing external code to cause deadlocks. Use private locks instead."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case d: Defn.Def if !isPrivate(d.mods) =>
        d.body match {
          case Term.Apply(Term.Name("synchronized"), _) =>
            Some(issue(
              "Synchronized block on public method; consider using a private lock object",
              d.pos,
              file,
              suggestion = Some("Use a private val lock = new Object and lock.synchronized { ... }")
            ))
          case _ => None
        }
    }.flatten
  }

  private def isPrivate(mods: List[Mod]): Boolean = {
    mods.exists {
      case Mod.Private(_) => true
      case _ => false
    }
  }
}

/**
 * Rule: Double-checked locking anti-pattern
 */
object DoubleCheckedLockingRule extends Rule {
  val id = "C005"
  val name = "double-checked-locking"
  val description = "Double-checked locking pattern is broken in JVM"
  val category = Category.Concurrency
  val severity = Severity.Error
  override val explanation = "Double-checked locking doesn't work correctly in Java/Scala due to memory model issues. Use lazy val or proper synchronization."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: if (x == null) { synchronized { if (x == null) ... } }
      case t @ Term.If(
            Term.ApplyInfix(_, Term.Name("=="), _, List(Lit.Null())),
            Term.Apply(Term.Name("synchronized"), List(
              Term.Block(List(Term.If(Term.ApplyInfix(_, Term.Name("=="), _, List(Lit.Null())), _, _)))
            )), _) =>
        issue(
          "Double-checked locking pattern detected; this is broken in JVM",
          t.pos,
          file,
          suggestion = Some("Use lazy val for lazy initialization, or AtomicReference")
        )
    }
  }
}

/**
 * Rule: Mutable state in Actor
 */
object MutableStateInActorRule extends Rule {
  val id = "C006"
  val name = "mutable-state-actor"
  val description = "Be careful with mutable state in Actors"
  val category = Category.Concurrency
  val severity = Severity.Info
  override val explanation = "While Actors serialize message processing, sharing mutable state via closures can still cause issues."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case cls: Defn.Class if extendsActor(cls) =>
        cls.templ.stats.collect {
          case d: Defn.Var =>
            issue(
              "Mutable var in Actor; ensure it's only accessed within message handlers",
              d.pos,
              file,
              suggestion = Some("Consider using become/unbecome for state changes, or context.become")
            )
        }
    }.flatten
  }

  private def extendsActor(cls: Defn.Class): Boolean = {
    cls.templ.inits.exists { init =>
      init.tpe.syntax.contains("Actor")
    }
  }
}

/**
 * Rule: Promise should be completed exactly once
 */
object PromiseCompletionRule extends Rule {
  val id = "C007"
  val name = "promise-completion"
  val description = "Promise should be completed exactly once"
  val category = Category.Concurrency
  val severity = Severity.Warning
  override val explanation = "Completing a Promise more than once throws IllegalStateException. Use trySuccess/tryFailure for safe completion."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Apply(Term.Select(_, Term.Name("success")), _) =>
        issue(
          "Promise.success throws if already completed; consider using trySuccess",
          t.pos,
          file,
          suggestion = Some("Use trySuccess for safe completion that returns Boolean")
        )
      case t @ Term.Apply(Term.Select(_, Term.Name("failure")), _) =>
        issue(
          "Promise.failure throws if already completed; consider using tryFailure",
          t.pos,
          file,
          suggestion = Some("Use tryFailure for safe completion that returns Boolean")
        )
    }
  }
}

/**
 * Rule: Avoid race conditions in lazy initialization
 */
object LazyInitRaceRule extends Rule {
  val id = "C008"
  val name = "lazy-init-race"
  val description = "Manual lazy initialization can have race conditions"
  val category = Category.Concurrency
  val severity = Severity.Warning
  override val explanation = "Manual lazy initialization patterns are error-prone. Use Scala's lazy val which is thread-safe."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect: var x = null; def getX = { if (x == null) x = ...; x }
      case d: Defn.Def =>
        d.body match {
          case Term.Block(stats) if stats.exists {
            case Term.If(Term.ApplyInfix(_, Term.Name("=="), _, List(Lit.Null())), _, _) => true
            case _ => false
          } =>
            Some(issue(
              "Manual lazy initialization pattern; consider using lazy val",
              d.pos,
              file,
              suggestion = Some("Use lazy val for thread-safe lazy initialization")
            ))
          case _ => None
        }
    }.flatten
  }
}

/**
 * All concurrency rules
 */
object ConcurrencyRules {
  val all: Seq[Rule] = Seq(
    VarInConcurrentCodeRule,
    BlockingInFutureRule,
    MissingExecutionContextRule,
    SynchronizedOnPublicRule,
    DoubleCheckedLockingRule,
    MutableStateInActorRule,
    PromiseCompletionRule,
    LazyInitRaceRule
  )
}
