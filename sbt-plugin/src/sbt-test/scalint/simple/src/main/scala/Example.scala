package example

object Example {
  def main(args: Array[String]): Unit = {
    println("Hello, ScalaLint!")
  }

  // Some code to lint
  def riskyMethod(): Unit = {
    var mutable = 0  // Could trigger style warning
    mutable += 1
    println(mutable)
  }
}
