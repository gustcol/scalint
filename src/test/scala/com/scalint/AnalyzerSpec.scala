package com.scalint

import com.scalint.core._
import com.scalint.rules.RuleRegistry
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AnalyzerSpec extends AnyFunSuite with Matchers {

  val analyzer = Analyzer.default

  test("analyzer should parse valid Scala code") {
    val code = """
      |object Test {
      |  def hello(): String = "Hello, World!"
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code, "Test.scala")
    result.parseError shouldBe None
  }

  test("analyzer should report parse errors for invalid code") {
    val code = """
      |object Test {
      |  def hello( = "missing parameter"
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code, "Test.scala")
    result.parseError shouldBe defined
  }

  test("analyzer should detect multiple issues in code") {
    val code = """
      |object test_object {
      |  var PASSWORD = "secret123"
      |  def DoSomething(): Unit = {
      |    val x = null
      |    return x
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code, "Test.scala")
    result.issues should not be empty
    result.issues.map(_.ruleId) should contain allOf ("S002", "B001", "S006")
  }

  test("analyzer should respect disabled rules") {
    val config = LintConfig(disabledRules = Set("S002", "B001"))
    val analyzer = Analyzer.withConfig(config)

    val code = """
      |object test_object {
      |  val x = null
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code, "Test.scala")
    result.issues.map(_.ruleId) should not contain allOf ("S002", "B001")
  }

  test("analyzer should respect enabled categories") {
    val config = LintConfig(enabledCategories = Set(Category.Security))
    val analyzer = Analyzer.withConfig(config)

    val code = """
      |object Test {
      |  val password = "secret123"
      |  var myVar = 1
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code, "Test.scala")
    result.issues.forall(_.category == Category.Security) shouldBe true
  }

  test("analyzer should respect minimum severity") {
    val config = LintConfig(minSeverity = Severity.Warning)
    val analyzer = Analyzer.withConfig(config)

    val code = """
      |object Test {
      |  def test(): Unit = {
      |    val x = null
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code, "Test.scala")
    result.issues.forall(_.severity >= Severity.Warning) shouldBe true
  }

  test("analyzer should count issues by severity correctly") {
    val code = """
      |object test {
      |  val x = null
      |  def DoSomething(): Unit = {
      |    return ()
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code, "Test.scala")
    val analysisResult = AnalysisResult(
      fileResults = Seq(result),
      totalFiles = 1,
      analyzedFiles = 1,
      skippedFiles = 0
    )

    analysisResult.totalIssueCount should be > 0
  }
}
