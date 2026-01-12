package com.scalint.rules

import com.scalint.core._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PerformanceRulesSpec extends AnyFunSuite with Matchers {

  val analyzer = Analyzer.default

  // P001: Use isEmpty instead of size == 0
  test("P001: should suggest isEmpty over size == 0") {
    val code = """
      |object Test {
      |  val list = List(1, 2, 3)
      |  val empty1 = list.size == 0
      |  val empty2 = list.length == 0
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val p001Issues = result.issues.filter(_.ruleId == "P001")
    p001Issues.size should be >= 1
  }

  test("P001: should suggest nonEmpty over size > 0") {
    val code = """
      |object Test {
      |  val list = List(1, 2, 3)
      |  val hasItems = list.size > 0
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val p001Issues = result.issues.filter(_.ruleId == "P001")
    p001Issues should have size 1
  }

  // P002: Multiple traversals
  test("P002: should detect filter followed by map") {
    val code = """
      |object Test {
      |  val numbers = List(1, 2, 3, 4, 5)
      |  val result = numbers.filter(_ > 2).map(_ * 2)
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val p002Issues = result.issues.filter(_.ruleId == "P002")
    p002Issues should have size 1
  }

  test("P002: should detect map followed by flatten") {
    val code = """
      |object Test {
      |  val nested = List(List(1, 2), List(3, 4))
      |  val flat = nested.map(_.map(_ * 2)).flatten
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val p002Issues = result.issues.filter(_.ruleId == "P002")
    p002Issues should have size 1
  }

  // P008: Use exists/forall
  test("P008: should suggest exists over find.isDefined") {
    val code = """
      |object Test {
      |  val list = List(1, 2, 3)
      |  val hasTwo = list.find(_ == 2).isDefined
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val p008Issues = result.issues.filter(_.ruleId == "P008")
    p008Issues should have size 1
  }
}
