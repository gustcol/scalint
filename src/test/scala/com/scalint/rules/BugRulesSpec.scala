package com.scalint.rules

import com.scalint.core._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BugRulesSpec extends AnyFunSuite with Matchers {

  val analyzer = Analyzer.default

  // B001: Avoid null
  test("B001: should detect null usage") {
    val code = """
      |object Test {
      |  val x: String = null
      |  val y = if (x == null) "empty" else x
      |  val z = if (x != null) x else "empty"
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b001Issues = result.issues.filter(_.ruleId == "B001")
    b001Issues.size should be >= 1
  }

  test("B001: should not flag Option.None") {
    val code = """
      |object Test {
      |  val x: Option[String] = None
      |  val y = x.getOrElse("default")
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b001Issues = result.issues.filter(_.ruleId == "B001")
    b001Issues shouldBe empty
  }

  // B004: Avoid .last
  test("B004: should detect .last on collections") {
    val code = """
      |object Test {
      |  val list = List(1, 2, 3)
      |  val lastElement = list.last
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b004Issues = result.issues.filter(_.ruleId == "B004")
    b004Issues should have size 1
  }

  // B005: Avoid throwing exceptions
  test("B005: should detect throw statements") {
    val code = """
      |object Test {
      |  def divide(a: Int, b: Int): Int = {
      |    if (b == 0) throw new ArithmeticException("Division by zero")
      |    a / b
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b005Issues = result.issues.filter(_.ruleId == "B005")
    b005Issues should have size 1
  }

  // B006: Unreachable code
  test("B006: should detect unreachable code after return") {
    val code = """
      |object Test {
      |  def test(): Int = {
      |    return 42
      |    val x = 10
      |    x
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b006Issues = result.issues.filter(_.ruleId == "B006")
    // Both `val x = 10` and `x` are unreachable after return
    b006Issues should have size 2
  }

  test("B006: should detect unreachable code after throw") {
    val code = """
      |object Test {
      |  def test(): Int = {
      |    throw new Exception("Error")
      |    println("This will never execute")
      |    0
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b006Issues = result.issues.filter(_.ruleId == "B006")
    b006Issues.size should be >= 1
  }

  // B007: Non-exhaustive match
  test("B007: should detect non-exhaustive pattern matching") {
    val code = """
      |object Test {
      |  sealed trait Animal
      |  case class Dog() extends Animal
      |  case class Cat() extends Animal
      |
      |  def describe(animal: Animal): String = animal match {
      |    case Dog() => "A dog"
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b007Issues = result.issues.filter(_.ruleId == "B007")
    b007Issues should have size 1
  }

  test("B007: should accept exhaustive pattern matching") {
    val code = """
      |object Test {
      |  def describe(x: Int): String = x match {
      |    case 1 => "one"
      |    case 2 => "two"
      |    case _ => "other"
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b007Issues = result.issues.filter(_.ruleId == "B007")
    b007Issues shouldBe empty
  }

  // B009: Var in case class
  test("B009: should detect var in case class") {
    val code = """
      |case class Person(name: String, var age: Int)
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b009Issues = result.issues.filter(_.ruleId == "B009")
    b009Issues should have size 1
  }

  test("B009: should accept val in case class") {
    val code = """
      |case class Person(name: String, age: Int)
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b009Issues = result.issues.filter(_.ruleId == "B009")
    b009Issues shouldBe empty
  }

  // B011: Empty catch block
  test("B011: should detect empty catch blocks") {
    val code = """
      |object Test {
      |  def test(): Unit = {
      |    try {
      |      riskyOperation()
      |    } catch {
      |      case _: Exception => // empty catch
      |    }
      |  }
      |
      |  def riskyOperation(): Unit = {}
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b011Issues = result.issues.filter(_.ruleId == "B011")
    b011Issues should have size 1
  }

  // B012: Boolean comparison
  test("B012: should detect redundant Boolean comparison") {
    val code = """
      |object Test {
      |  val flag = true
      |  val result1 = flag == true
      |  val result2 = flag == false
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val b012Issues = result.issues.filter(_.ruleId == "B012")
    b012Issues.size should be >= 1
  }
}
