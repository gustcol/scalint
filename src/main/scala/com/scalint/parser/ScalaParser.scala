package com.scalint.parser

import scala.meta._
import scala.meta.dialects
import java.io.File
import java.nio.file.{Files, Paths}
import scala.util.{Try, Success, Failure}

/**
 * Scala source code parser using Scalameta
 */
object ScalaParser {

  /**
   * Supported Scala dialects
   */
  sealed trait ScalaDialect {
    def dialect: Dialect
    def name: String
  }

  object ScalaDialect {
    case object Scala213 extends ScalaDialect {
      val dialect: Dialect = dialects.Scala213
      val name = "scala213"
    }
    case object Scala3 extends ScalaDialect {
      val dialect: Dialect = dialects.Scala3
      val name = "scala3"
    }
    case object Scala212 extends ScalaDialect {
      val dialect: Dialect = dialects.Scala212
      val name = "scala212"
    }
    case object Sbt extends ScalaDialect {
      val dialect: Dialect = dialects.Sbt1
      val name = "sbt"
    }

    val default: ScalaDialect = Scala213

    def fromString(s: String): ScalaDialect = s.toLowerCase match {
      case "scala3" | "3" | "dotty" => Scala3
      case "scala212" | "212" | "2.12" => Scala212
      case "sbt" | "sbt1" => Sbt
      case _ => Scala213
    }
  }

  /**
   * Result of parsing a file
   */
  sealed trait ParseResult
  case class ParseSuccess(source: Source, file: String) extends ParseResult
  case class ParseError(file: String, error: String, line: Option[Int] = None) extends ParseResult

  /**
   * Parse a single file
   */
  def parseFile(filePath: String, dialect: ScalaDialect = ScalaDialect.default): ParseResult = {
    Try {
      val path = Paths.get(filePath)
      val content = new String(Files.readAllBytes(path), "UTF-8")
      parseString(content, filePath, dialect)
    } match {
      case Success(result) => result
      case Failure(e) => ParseError(filePath, s"Failed to read file: ${e.getMessage}")
    }
  }

  /**
   * Parse source code from a string
   */
  def parseString(
    content: String,
    fileName: String = "<input>",
    dialect: ScalaDialect = ScalaDialect.default
  ): ParseResult = {
    implicit val d: Dialect = dialect.dialect

    content.parse[Source] match {
      case Parsed.Success(tree) =>
        ParseSuccess(tree, fileName)
      case Parsed.Error(pos, message, _) =>
        ParseError(
          fileName,
          message,
          Some(pos.startLine + 1)
        )
    }
  }

  /**
   * Parse multiple files
   */
  def parseFiles(
    files: Seq[String],
    dialect: ScalaDialect = ScalaDialect.default
  ): Seq[ParseResult] = {
    files.map(f => parseFile(f, dialect))
  }

  /**
   * Find all Scala files in a directory
   */
  def findScalaFiles(
    directory: String,
    includePatterns: Seq[String] = Seq("**/*.scala", "**/*.sc"),
    excludePatterns: Seq[String] = Seq.empty
  ): Seq[String] = {
    import java.nio.file.{FileSystems, FileVisitOption, Path}
    import scala.jdk.CollectionConverters._

    val dir = Paths.get(directory)
    if (!Files.exists(dir) || !Files.isDirectory(dir)) {
      return Seq.empty
    }

    val matcher = FileSystems.getDefault

    def matchesPattern(path: Path, patterns: Seq[String]): Boolean = {
      patterns.exists { pattern =>
        val globMatcher = matcher.getPathMatcher(s"glob:$pattern")
        val relativePath = dir.relativize(path)
        globMatcher.matches(relativePath) || globMatcher.matches(path.getFileName)
      }
    }

    Files.walk(dir, FileVisitOption.FOLLOW_LINKS)
      .iterator()
      .asScala
      .filter(Files.isRegularFile(_))
      .filter(p => matchesPattern(p, includePatterns))
      .filterNot(p => matchesPattern(p, excludePatterns))
      .map(_.toString)
      .toSeq
      .sorted
  }

  /**
   * Detect dialect from file extension or content
   */
  def detectDialect(filePath: String): ScalaDialect = {
    if (filePath.endsWith(".sbt")) ScalaDialect.Sbt
    else if (filePath.endsWith(".sc")) ScalaDialect.Scala213
    else ScalaDialect.default
  }

  /**
   * Extract useful metadata from parsed source
   */
  case class SourceMetadata(
    packageName: Option[String],
    imports: Seq[String],
    classes: Seq[String],
    objects: Seq[String],
    traits: Seq[String],
    defs: Seq[String]
  )

  def extractMetadata(source: Source): SourceMetadata = {
    val packageName = source.collect {
      case pkg: Pkg => pkg.ref.syntax
    }.headOption

    val imports = source.collect {
      case i: Import => i.syntax
    }

    val classes = source.collect {
      case cls: Defn.Class => cls.name.value
    }

    val objects = source.collect {
      case obj: Defn.Object => obj.name.value
    }

    val traits = source.collect {
      case t: Defn.Trait => t.name.value
    }

    val defs = source.collect {
      case d: Defn.Def => d.name.value
    }

    SourceMetadata(packageName, imports, classes, objects, traits, defs)
  }
}
