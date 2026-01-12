package com.scalint.rules

import com.scalint.core._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StyleRulesSpec extends AnyFunSuite with Matchers {

  val analyzer = Analyzer.default

  // S001: Class naming
  test("S001: should detect non-PascalCase class names") {
    val code = """
      |class myClass {}
      |class my_class {}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s001Issues = result.issues.filter(_.ruleId == "S001")
    s001Issues should have size 2
  }

  test("S001: should accept valid PascalCase class names") {
    val code = """
      |class MyClass {}
      |class HTTPClient {}
      |class User {}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s001Issues = result.issues.filter(_.ruleId == "S001")
    s001Issues shouldBe empty
  }

  // S002: Object naming
  test("S002: should detect non-PascalCase object names") {
    val code = """
      |object myObject {}
      |object my_object {}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s002Issues = result.issues.filter(_.ruleId == "S002")
    s002Issues should have size 2
  }

  // S003: Method naming
  test("S003: should detect non-camelCase method names") {
    val code = """
      |object Test {
      |  def DoSomething(): Unit = {}
      |  def do_something(): Unit = {}
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s003Issues = result.issues.filter(_.ruleId == "S003")
    s003Issues should have size 2
  }

  test("S003: should accept valid camelCase and operator methods") {
    val code = """
      |object Test {
      |  def doSomething(): Unit = {}
      |  def getUserById(id: Int): Unit = {}
      |  def +(other: Int): Int = 0
      |  def apply(): Unit = {}
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s003Issues = result.issues.filter(_.ruleId == "S003")
    s003Issues shouldBe empty
  }

  // S006: Avoid return
  test("S006: should detect explicit return statements") {
    val code = """
      |object Test {
      |  def getValue(): Int = {
      |    return 42
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s006Issues = result.issues.filter(_.ruleId == "S006")
    s006Issues should have size 1
  }

  // S007: String interpolation
  test("S007: should suggest string interpolation over concatenation") {
    val code = """
      |object Test {
      |  val name = "World"
      |  val greeting = "Hello, " + name
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s007Issues = result.issues.filter(_.ruleId == "S007")
    s007Issues should have size 1
  }

  // S008: Line length
  test("S008: should detect lines exceeding 120 characters") {
    val longLine = "val x = " + "a" * 150
    val code = s"""
      |object Test {
      |  $longLine
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s008Issues = result.issues.filter(_.ruleId == "S008")
    s008Issues should have size 1
  }

  // S011: Wildcard imports
  test("S011: should detect wildcard imports") {
    val code = """
      |import scala.collection.mutable._
      |import java.util._
      |
      |object Test {}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val s011Issues = result.issues.filter(_.ruleId == "S011")
    s011Issues should have size 2
  }
}
