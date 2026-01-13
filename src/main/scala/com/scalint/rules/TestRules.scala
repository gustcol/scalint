package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: Test assertions in production code
 */
object TestInProductionRule extends Rule {
  val id = "TST001"
  val name = "test-in-production"
  val description = "Test utilities should not be used in production code"
  val category = Category.Test
  val severity = Severity.Error
  override val explanation = "ScalaTest, JUnit, Specs2, and other test framework imports in production " +
    "code indicate test utilities leaking into main sources. This can bloat dependencies and indicate architectural issues."

  private val testImports = Set(
    "org.scalatest",
    "org.junit",
    "org.specs2",
    "org.mockito",
    "org.scalamock",
    "org.scalacheck",
    "com.holdenkarau.spark.testing",
    "munit",
    "utest",
    "zio.test"
  )

  private val testClasses = Set(
    "FlatSpec", "FunSpec", "WordSpec", "FunSuite", "AnyFlatSpec",
    "AnyFunSpec", "AnyWordSpec", "AnyFunSuite", "Matchers", "Assertions",
    "BeforeAndAfter", "BeforeAndAfterAll", "MockFactory"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Skip if this is clearly a test file
    val isTestFile = file.contains("/test/") ||
      file.contains("Test.scala") ||
      file.contains("Spec.scala") ||
      file.contains("Suite.scala") ||
      file.endsWith("Tests.scala")

    if (isTestFile) return Seq.empty

    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.collect {
      // Detect test framework imports
      case i @ Import(importers) =>
        importers.foreach { importer =>
          val importPath = importer.ref.syntax
          testImports.find(ti => importPath.startsWith(ti)).foreach { framework =>
            issues += issue(
              s"Test framework import '$importPath' in production code",
              i.pos,
              file,
              suggestion = Some(s"Move this code to src/test or remove the test dependency")
            )
          }
        }

      // Detect test class extension
      case t @ Defn.Class(_, _, _, _, Template(_, inits, _, _)) =>
        inits.foreach { init =>
          val typeName = init.tpe.syntax
          if (testClasses.exists(typeName.contains)) {
            issues += issue(
              s"Test class '$typeName' extended in production code",
              t.pos,
              file,
              suggestion = Some("Move test classes to src/test directory")
            )
          }
        }

      // Detect assert() which might be from test frameworks
      case t @ Term.Apply(Term.Name("assert"), args) if args.size == 1 =>
        issues += issue(
          "assert() in production code - use exceptions or require() for validation",
          t.pos,
          file,
          suggestion = Some("Use require() for preconditions or throw specific exceptions")
        )
    }

    issues.toSeq
  }
}

/**
 * Rule: Mocking library not isolated
 */
object MockingNotIsolatedRule extends Rule {
  val id = "TST002"
  val name = "mocking-not-isolated"
  val description = "Mocking frameworks should only be used in test code"
  val category = Category.Test
  val severity = Severity.Warning
  override val explanation = "Using Mockito, ScalaMock, or other mocking frameworks in production " +
    "code is a code smell. Mocks should only be used in tests; production code should use real implementations or dependency injection."

  private val mockingImports = Set(
    "org.mockito",
    "org.scalamock",
    "com.github.tomakehurst.wiremock",
    "mock"
  )

  private val mockMethods = Set("mock", "spy", "when", "verify", "doReturn", "doThrow", "stub")

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Skip test files
    val isTestFile = file.contains("/test/") ||
      file.contains("Test.scala") ||
      file.contains("Spec.scala") ||
      file.contains("Mock") // Allow explicit mock implementations

    if (isTestFile) return Seq.empty

    val issues = scala.collection.mutable.ArrayBuffer[LintIssue]()

    source.collect {
      // Detect mocking imports
      case i @ Import(importers) =>
        importers.foreach { importer =>
          val importPath = importer.ref.syntax
          if (mockingImports.exists(importPath.contains)) {
            issues += issue(
              s"Mocking framework import '$importPath' in production code",
              i.pos,
              file,
              suggestion = Some("Use dependency injection instead of mocks in production")
            )
          }
        }

      // Detect mock() calls
      case t @ Term.Apply(Term.Name(method), _) if mockMethods.contains(method) =>
        issues += issue(
          s"Mocking method '$method()' used in production code",
          t.pos,
          file,
          suggestion = Some("Extract interfaces and use real implementations or DI")
        )

      case t @ Term.Apply(Term.Select(_, Term.Name(method)), _) if mockMethods.contains(method) =>
        issues += issue(
          s"Mocking method '$method()' used in production code",
          t.pos,
          file,
          suggestion = Some("Consider refactoring to use proper dependency injection")
        )
    }

    issues.toSeq
  }
}

/**
 * Rule: Test without assertions
 */
object TestWithoutAssertionRule extends Rule {
  val id = "TST003"
  val name = "test-without-assertion"
  val description = "Test method appears to have no assertions"
  val category = Category.Test
  val severity = Severity.Warning
  override val explanation = "A test without assertions doesn't verify anything. " +
    "Every test should have at least one assertion or expectation."

  private val assertionMethods = Set(
    "assert", "assertEquals", "assertTrue", "assertFalse", "assertThrows",
    "should", "shouldBe", "shouldEqual", "mustBe", "must", "mustEqual",
    "expect", "expectResult", "intercept", "assertResult", "===", "!==",
    "verify", "check", "forAll", "property"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Only check test files
    val isTestFile = file.contains("/test/") ||
      file.contains("Test.scala") ||
      file.contains("Spec.scala")

    if (!isTestFile) return Seq.empty

    source.collect {
      // ScalaTest test blocks
      case t @ Term.ApplyInfix(
            Lit.String(_),  // test name
            Term.Name(testWord),
            _,
            List(body)) if Set("in", "should", "must").contains(testWord) =>
        if (!hasAssertion(body)) {
          Seq(issue(
            "Test appears to have no assertions",
            t.pos,
            file,
            suggestion = Some("Add assertions to verify expected behavior")
          ))
        } else Seq.empty

      // test() method style
      case t @ Term.Apply(Term.Name("test"), List(_, body: Term.Block)) =>
        if (!hasAssertion(body)) {
          Seq(issue(
            "Test block appears to have no assertions",
            t.pos,
            file,
            suggestion = Some("Add assertions using assert(), assertEquals(), or matchers")
          ))
        } else Seq.empty
    }.flatten
  }

  private def hasAssertion(tree: Tree): Boolean = {
    var found = false
    tree.traverse {
      case Term.Name(name) if assertionMethods.contains(name) => found = true
      case Term.Select(_, Term.Name(name)) if assertionMethods.contains(name) => found = true
      case _ =>
    }
    found
  }
}

/**
 * Rule: Flaky test patterns
 */
object FlakyTestPatternRule extends Rule {
  val id = "TST004"
  val name = "flaky-test-pattern"
  val description = "Pattern that often causes flaky tests detected"
  val category = Category.Test
  val severity = Severity.Warning
  override val explanation = "Certain patterns like Thread.sleep(), fixed timestamps, and random values " +
    "without seeds often cause tests to fail intermittently."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Only check test files
    val isTestFile = file.contains("/test/") ||
      file.contains("Test.scala") ||
      file.contains("Spec.scala")

    if (!isTestFile) return Seq.empty

    source.collect {
      // Thread.sleep in tests
      case t @ Term.Apply(Term.Select(Term.Name("Thread"), Term.Name("sleep")), _) =>
        Seq(issue(
          "Thread.sleep() in tests causes flakiness",
          t.pos,
          file,
          suggestion = Some("Use Awaitility, ScalaTest's Eventually, or test-specific timing utilities")
        ))

      // System.currentTimeMillis for assertions
      case t @ Term.Select(Term.Name("System"), Term.Name("currentTimeMillis")) =>
        Seq(issue(
          "System.currentTimeMillis() in tests can cause timing-dependent failures",
          t.pos,
          file,
          suggestion = Some("Use Clock abstraction or freeze time with libraries like java-time-testing")
        ))

      // Random without seed
      case t @ Term.New(Init(Type.Name("Random"), _, List(List()))) =>
        Seq(issue(
          "Random without seed causes non-reproducible tests",
          t.pos,
          file,
          suggestion = Some("Provide a fixed seed: new Random(42L)")
        ))

      // scala.util.Random usage
      case t @ Term.Select(Term.Select(Term.Select(Term.Name("scala"), Term.Name("util")), Term.Name("Random")), _) =>
        Seq(issue(
          "scala.util.Random without seed causes non-reproducible tests",
          t.pos,
          file,
          suggestion = Some("Use new Random(seed) for reproducible tests")
        ))
    }.flatten
  }
}

/**
 * Rule: Test pollution (shared mutable state)
 */
object TestPollutionRule extends Rule {
  val id = "TST005"
  val name = "test-pollution"
  val description = "Shared mutable state between tests detected"
  val category = Category.Test
  val severity = Severity.Warning
  override val explanation = "Mutable variables (var) at class level in test suites can cause test pollution " +
    "where tests affect each other. Use fresh fixtures for each test."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Only check test files
    val isTestFile = file.contains("/test/") ||
      file.contains("Test.scala") ||
      file.contains("Spec.scala")

    if (!isTestFile) return Seq.empty

    source.collect {
      // Class-level var in test class
      case cls @ Defn.Class(_, _, _, _, Template(_, inits, _, stats)) =>
        val isTestClass = inits.exists(init =>
          init.tpe.syntax.contains("Spec") ||
          init.tpe.syntax.contains("Suite") ||
          init.tpe.syntax.contains("Test"))

        if (isTestClass) {
          stats.collect {
            case v @ Defn.Var(_, pats, _, _) =>
              pats.collect {
                case Pat.Var(name) =>
                  issue(
                    s"Mutable variable '${name.value}' at class level can cause test pollution",
                    v.pos,
                    file,
                    suggestion = Some("Use fresh fixtures: def fixture = { ... } or BeforeEach")
                  )
              }
          }.flatten
        } else Seq.empty
    }.flatten
  }
}

/**
 * All Test rules
 */
object TestRules {
  val all: Seq[Rule] = Seq(
    TestInProductionRule,
    MockingNotIsolatedRule,
    TestWithoutAssertionRule,
    FlakyTestPatternRule,
    TestPollutionRule
  )
}
