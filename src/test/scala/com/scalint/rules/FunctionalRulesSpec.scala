package com.scalint.rules

import com.scalint.core._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FunctionalRulesSpec extends AnyFunSuite with Matchers {

  val analyzer = Analyzer.default

  // F001: Prefer immutable collections
  test("F001: should warn about mutable collection imports") {
    val code = """
      |import scala.collection.mutable
      |import scala.collection.mutable.ArrayBuffer
      |
      |object Test {
      |  val buffer = ArrayBuffer[Int]()
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val f001Issues = result.issues.filter(_.ruleId == "F001")
    f001Issues should not be empty
  }

  // F003: Prefer pattern matching over isInstanceOf
  test("F003: should warn about isInstanceOf usage") {
    val code = """
      |object Test {
      |  def check(x: Any): Boolean = {
      |    x.isInstanceOf[String]
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val f003Issues = result.issues.filter(_.ruleId == "F003")
    f003Issues should have size 1
  }

  test("F003: should warn about asInstanceOf usage") {
    val code = """
      |object Test {
      |  def convert(x: Any): String = {
      |    x.asInstanceOf[String]
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val f003Issues = result.issues.filter(_.ruleId == "F003")
    f003Issues should have size 1
  }

  // F005: Use Option instead of null checks
  test("F005: should suggest Option for null checks") {
    val code = """
      |object Test {
      |  def wrap(x: String): Option[String] = {
      |    if (x != null) Some(x) else None
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val f005Issues = result.issues.filter(_.ruleId == "F005")
    f005Issues should have size 1
  }

  // F006: Avoid while loops
  test("F006: should warn about while loops") {
    val code = """
      |object Test {
      |  def countdown(n: Int): Unit = {
      |    var i = n
      |    while (i > 0) {
      |      println(i)
      |      i -= 1
      |    }
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val f006Issues = result.issues.filter(_.ruleId == "F006")
    f006Issues should have size 1
  }

  // F008: Use collect
  test("F008: should suggest collect over filter+map") {
    val code = """
      |object Test {
      |  val numbers = List(1, 2, 3, 4, 5)
      |  val doubled = numbers.filter(_ > 2).map(_ * 2)
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val f008Issues = result.issues.filter(_.ruleId == "F008")
    f008Issues should have size 1
  }

  // F009: Avoid Any
  test("F009: should warn about Any return type") {
    val code = """
      |object Test {
      |  def getValue(): Any = "hello"
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val f009Issues = result.issues.filter(_.ruleId == "F009")
    f009Issues should have size 1
  }
}
