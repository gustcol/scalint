package com.example

import scala.collection.mutable._
import java.security.MessageDigest
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.Random
import java.io.{File, ObjectInputStream}

// Bad: Object name not PascalCase (S002)
object user_service {

  // Bad: Hardcoded credentials (SEC004)
  val password = "supersecret123"
  val api_key = "abcd1234"

  // Bad: Using var (C001 - var in concurrent context)
  var counter = 0

  // Bad: Method name not camelCase (S003)
  def GetUserById(id: Long): String = {
    // Bad: Using null (B001)
    val user: String = null

    // Bad: Comparing with null (B001)
    if (user == null) {
      return "not found"  // Bad: Using return (S006)
    }

    user
  }

  // Bad: Using Any as return type (F009)
  def processData(): Any = {
    "result"
  }

  // Bad: Using weak cryptography (SEC006)
  def hashPassword(pwd: String): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    md.digest(pwd.getBytes)
  }

  // Bad: Non-exhaustive match without wildcard (B007)
  def describe(num: Int): String = num match {
    case 1 => "one"
    case 2 => "two"
  }

  // Bad: Using isInstanceOf (F003)
  def checkType(x: Any): Boolean = {
    x.isInstanceOf[String]
  }

  // Bad: Empty catch block (B011)
  def riskyOperation(): Unit = {
    try {
      throw new Exception("Error")
    } catch {
      case _: Exception =>
    }
  }

  // Bad: Using .head on collection (B003)
  def getFirst(list: List[Int]): Int = {
    list.head
  }

  // Bad: Using .last on collection (B004)
  def getLast(list: List[Int]): Int = {
    list.last
  }

  // Bad: size == 0 instead of isEmpty (P001)
  def checkEmpty(list: List[Int]): Boolean = {
    list.size == 0
  }

  // Bad: filter + map instead of collect (P002, F008)
  def processNumbers(nums: List[Int]): List[Int] = {
    nums.filter(_ > 0).map(_ * 2)
  }

  // Bad: find.isDefined instead of exists (P008)
  def hasPositive(nums: List[Int]): Boolean = {
    nums.find(_ > 0).isDefined
  }

  // Bad: Throwing exception in pure function (B005)
  def divide(a: Int, b: Int): Int = {
    if (b == 0) throw new ArithmeticException("Division by zero")
    a / b
  }

  // Bad: While loop (F006)
  def countdown(n: Int): Unit = {
    var i = n
    while (i > 0) {
      println(i)
      i -= 1
    }
  }

  // Bad: Boolean comparison with literal (B012)
  def checkFlag(flag: Boolean): Boolean = {
    flag == true
  }

  // Bad: var in case class (B009)
  case class MutablePerson(name: String, var age: Int)

  // Bad: Very long line that exceeds 120 characters and makes the code harder to read (S008)
  val veryLongVariableName = "This is a string that is way too long and should be broken into multiple lines for better readability"

  // Bad: Using Option.get (B002)
  def unsafeGet(opt: Option[String]): String = {
    opt.get
  }

  // Bad: Float comparison with == (B008)
  def compareFloats(a: Double, b: Double): Boolean = {
    a == 1.0
  }

  // Bad: String concatenation (S007)
  def greet(name: String): String = {
    "Hello, " + name + "!"
  }

  // Bad: Insecure random (SEC007)
  def generateToken(): Int = {
    val random = new Random()
    random.nextInt()
  }

  // Bad: Unreachable code (B006)
  def unreachableExample(): Int = {
    return 42
    val x = 10  // This is unreachable
    x
  }

  // Bad: SQL injection vulnerability (SEC001)
  def findUser(userId: String): String = {
    val query = "SELECT * FROM users WHERE id = " + userId
    query
  }

  // Bad: Command injection (SEC002)
  def runCommand(cmd: String): Unit = {
    Runtime.getRuntime.exec(cmd)
  }

  // Bad: Path traversal (SEC003)
  def readFile(filename: String): Unit = {
    val file = new File(filename)
    println(file.getAbsolutePath)
  }

  // Bad: Unsafe deserialization (SEC005)
  def deserialize(stream: java.io.InputStream): Any = {
    val ois = new ObjectInputStream(stream)
    ois.readObject()
  }

  // Bad: Logging sensitive data (SEC008)
  def logUser(username: String, userPassword: String): Unit = {
    println(s"User: $username, Password: $userPassword")
  }

  // Bad: Side effects in map (F002)
  def printNumbers(nums: List[Int]): List[Int] = {
    nums.map { n =>
      println(n)
      n * 2
    }
  }

  // Bad: Mutable collection in public API (B010)
  def getMutableList(): ArrayBuffer[Int] = {
    ArrayBuffer(1, 2, 3)
  }

  // Bad: Blocking in Future (C002)
  def blockingFuture(): Future[String] = {
    Future {
      Thread.sleep(1000)
      "done"
    }
  }
}

// Bad: Class name not PascalCase (S001)
class badClassName {
  def doSomething(): Unit = ()
}

// Bad: Data class without case (F010)
class DataHolder(val id: Int, val name: String)
