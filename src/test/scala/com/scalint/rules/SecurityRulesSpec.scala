package com.scalint.rules

import com.scalint.core._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SecurityRulesSpec extends AnyFunSuite with Matchers {

  val analyzer = Analyzer.default

  // SEC004: Hardcoded credentials
  test("SEC004: should detect hardcoded passwords") {
    val code = """
      |object Config {
      |  val password = "supersecret123"
      |  val apiKey = "abc123xyz789"
      |  val token = "bearer-token-value"
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val secIssues = result.issues.filter(_.ruleId == "SEC004")
    secIssues.size should be >= 2
  }

  test("SEC004: should not flag non-sensitive variables") {
    val code = """
      |object Config {
      |  val name = "John"
      |  val city = "New York"
      |  val count = 42
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val secIssues = result.issues.filter(_.ruleId == "SEC004")
    secIssues shouldBe empty
  }

  // SEC005: Unsafe deserialization
  test("SEC005: should detect ObjectInputStream usage") {
    val code = """
      |import java.io._
      |
      |object Deserializer {
      |  def deserialize(bytes: Array[Byte]): Any = {
      |    val bis = new ByteArrayInputStream(bytes)
      |    val ois = new ObjectInputStream(bis)
      |    ois.readObject()
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val secIssues = result.issues.filter(_.ruleId == "SEC005")
    secIssues.size should be >= 1
  }

  // SEC006: Weak cryptography
  test("SEC006: should detect weak hash algorithms") {
    val code = """
      |import java.security.MessageDigest
      |
      |object Hasher {
      |  def hashMD5(data: String): Array[Byte] = {
      |    val md = MessageDigest.getInstance("MD5")
      |    md.digest(data.getBytes)
      |  }
      |
      |  def hashSHA1(data: String): Array[Byte] = {
      |    val md = MessageDigest.getInstance("SHA1")
      |    md.digest(data.getBytes)
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val secIssues = result.issues.filter(_.ruleId == "SEC006")
    secIssues should have size 2
  }

  test("SEC006: should accept strong hash algorithms") {
    val code = """
      |import java.security.MessageDigest
      |
      |object Hasher {
      |  def hashSHA256(data: String): Array[Byte] = {
      |    val md = MessageDigest.getInstance("SHA-256")
      |    md.digest(data.getBytes)
      |  }
      |}
    """.stripMargin

    val result = analyzer.analyzeString(code)
    val secIssues = result.issues.filter(_.ruleId == "SEC006")
    secIssues shouldBe empty
  }
}
