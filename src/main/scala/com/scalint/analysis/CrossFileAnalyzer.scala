package com.scalint.analysis

import com.scalint.core._
import scala.meta._

/**
 * Cross-File Analysis
 *
 * Analyzes patterns and issues that span multiple files:
 * - Unused exports (public methods never called from other files)
 * - Circular dependencies between packages
 * - Inconsistent naming across modules
 * - Duplicated code patterns
 * - Missing companion objects
 */
object CrossFileAnalyzer {

  /**
   * Represents a symbol exported from a file
   */
  case class ExportedSymbol(
    name: String,
    kind: SymbolKind,
    file: String,
    position: SourcePosition,
    isPublic: Boolean
  )

  /**
   * Represents an import/usage of a symbol
   */
  case class SymbolUsage(
    name: String,
    file: String,
    position: SourcePosition,
    importPath: Option[String]
  )

  /**
   * Symbol kinds
   */
  sealed trait SymbolKind
  object SymbolKind {
    case object Class extends SymbolKind
    case object Object extends SymbolKind
    case object Trait extends SymbolKind
    case object Method extends SymbolKind
    case object Val extends SymbolKind
    case object Type extends SymbolKind
  }

  /**
   * Project-wide analysis result
   */
  case class CrossFileResult(
    unusedExports: Seq[UnusedExportIssue],
    circularDependencies: Seq[CircularDependencyIssue],
    duplicatedPatterns: Seq[DuplicatedPatternIssue],
    namingInconsistencies: Seq[NamingInconsistencyIssue]
  ) {
    def allIssues: Seq[LintIssue] = {
      unusedExports.map(_.toIssue) ++
      circularDependencies.map(_.toIssue) ++
      duplicatedPatterns.map(_.toIssue) ++
      namingInconsistencies.map(_.toIssue)
    }

    def issueCount: Int = allIssues.size
  }

  case class UnusedExportIssue(
    symbol: ExportedSymbol,
    suggestion: String
  ) {
    def toIssue: LintIssue = LintIssue(
      ruleId = "XF001",
      ruleName = "unused-export",
      category = Category.Complexity,
      severity = Severity.Info,
      message = s"Public ${symbol.kind} '${symbol.name}' appears unused in project",
      position = symbol.position,
      suggestion = Some(suggestion),
      explanation = Some("Public symbols that are never used from other files may indicate dead code " +
        "or could be made private to reduce API surface.")
    )
  }

  case class CircularDependencyIssue(
    packages: Seq[String],
    files: Seq[String]
  ) {
    def toIssue: LintIssue = LintIssue(
      ruleId = "XF002",
      ruleName = "circular-dependency",
      category = Category.Complexity,
      severity = Severity.Warning,
      message = s"Circular dependency detected: ${packages.mkString(" -> ")} -> ${packages.head}",
      position = SourcePosition(files.headOption.getOrElse(""), 1, 1, 1, 1),
      suggestion = Some("Break the cycle by extracting shared code to a common package"),
      explanation = Some("Circular dependencies make code harder to understand, test, and maintain. " +
        "They also prevent incremental compilation.")
    )
  }

  case class DuplicatedPatternIssue(
    pattern: String,
    locations: Seq[(String, Int)], // (file, line)
    similarity: Double
  ) {
    def toIssue: LintIssue = LintIssue(
      ruleId = "XF003",
      ruleName = "duplicated-pattern",
      category = Category.Complexity,
      severity = Severity.Info,
      message = s"Similar code pattern found in ${locations.size} locations",
      position = SourcePosition(locations.head._1, locations.head._2, 1, locations.head._2 + 5, 1),
      suggestion = Some("Consider extracting to a shared utility method"),
      explanation = Some("Duplicated code leads to maintenance burden. When fixing bugs or making " +
        "changes, all copies need to be updated.")
    )
  }

  case class NamingInconsistencyIssue(
    pattern: String,
    variants: Seq[(String, String)], // (name, file)
    suggestedConvention: String
  ) {
    def toIssue: LintIssue = LintIssue(
      ruleId = "XF004",
      ruleName = "naming-inconsistency",
      category = Category.Style,
      severity = Severity.Hint,
      message = s"Inconsistent naming for '$pattern': ${variants.map(_._1).distinct.mkString(", ")}",
      position = SourcePosition(variants.head._2, 1, 1, 1, 1),
      suggestion = Some(s"Consider standardizing to: $suggestedConvention"),
      explanation = Some("Consistent naming across a codebase improves readability and reduces " +
        "cognitive load when navigating code.")
    )
  }

  /**
   * Analyze multiple files together
   */
  def analyze(
    parsedFiles: Map[String, Source],
    config: LintConfig
  ): CrossFileResult = {
    // Extract symbols and usages
    val exports = extractExports(parsedFiles)
    val usages = extractUsages(parsedFiles)
    val packageDeps = extractPackageDependencies(parsedFiles)

    CrossFileResult(
      unusedExports = findUnusedExports(exports, usages),
      circularDependencies = findCircularDependencies(packageDeps),
      duplicatedPatterns = findDuplicatedPatterns(parsedFiles),
      namingInconsistencies = findNamingInconsistencies(exports)
    )
  }

  /**
   * Extract all exported (public) symbols from files
   */
  private def extractExports(files: Map[String, Source]): Seq[ExportedSymbol] = {
    files.flatMap { case (file, source) =>
      source.collect {
        case d @ Defn.Class(mods, name, _, _, _) if isPublic(mods) =>
          Seq(ExportedSymbol(name.value, SymbolKind.Class, file, SourcePosition.fromMeta(d.pos, file), true))

        case d @ Defn.Object(mods, name, _) if isPublic(mods) =>
          Seq(ExportedSymbol(name.value, SymbolKind.Object, file, SourcePosition.fromMeta(d.pos, file), true))

        case d @ Defn.Trait(mods, name, _, _, _) if isPublic(mods) =>
          Seq(ExportedSymbol(name.value, SymbolKind.Trait, file, SourcePosition.fromMeta(d.pos, file), true))

        case d @ Defn.Def(mods, name, _, _, _, _) if isPublic(mods) =>
          Seq(ExportedSymbol(name.value, SymbolKind.Method, file, SourcePosition.fromMeta(d.pos, file), true))

        case _ => Seq.empty
      }.flatten
    }.toSeq
  }

  /**
   * Extract all symbol usages (imports and references)
   */
  private def extractUsages(files: Map[String, Source]): Seq[SymbolUsage] = {
    files.flatMap { case (file, source) =>
      // Extract imports
      val imports = source.collect {
        case Import(importers) =>
          importers.flatMap { importer =>
            importer.importees.collect {
              case Importee.Name(name) =>
                SymbolUsage(name.value, file, SourcePosition.fromMeta(name.pos, file), Some(importer.ref.syntax))
            }
          }
      }.flatten

      // Extract type references
      val typeRefs = source.collect {
        case t @ Type.Name(name) =>
          Seq(SymbolUsage(name, file, SourcePosition.fromMeta(t.pos, file), None))
        case _ => Seq.empty
      }.flatten

      imports ++ typeRefs
    }.toSeq
  }

  /**
   * Extract package dependencies from imports
   */
  private def extractPackageDependencies(files: Map[String, Source]): Map[String, Set[String]] = {
    files.flatMap { case (file, source) =>
      // Get package name
      val packageName = source.collect {
        case Pkg(ref, _) => ref.syntax
      }.headOption.getOrElse("")

      // Get imported packages
      val importedPackages = source.collect {
        case Import(importers) =>
          importers.map { importer =>
            val parts = importer.ref.syntax.split("\\.")
            if (parts.length > 1) parts.init.mkString(".") else parts.mkString
          }
      }.flatten.toSet

      if (packageName.nonEmpty) Some(packageName -> importedPackages) else None
    }
  }

  /**
   * Find unused public exports
   */
  private def findUnusedExports(
    exports: Seq[ExportedSymbol],
    usages: Seq[SymbolUsage]
  ): Seq[UnusedExportIssue] = {
    val usedNames = usages.map(_.name).toSet

    exports.filterNot { exp =>
      usedNames.contains(exp.name) ||
      exp.kind == SymbolKind.Object || // Objects often used implicitly
      exp.name == "apply" ||
      exp.name == "unapply" ||
      exp.name.startsWith("_")
    }.map { exp =>
      UnusedExportIssue(
        exp,
        s"Consider making '${exp.name}' private or package-private if not used externally"
      )
    }
  }

  /**
   * Find circular dependencies between packages
   */
  private def findCircularDependencies(
    dependencies: Map[String, Set[String]]
  ): Seq[CircularDependencyIssue] = {
    val cycles = scala.collection.mutable.Set[Seq[String]]()

    def findCycles(
      current: String,
      path: Seq[String],
      visited: Set[String]
    ): Unit = {
      if (path.contains(current)) {
        val cycleStart = path.indexOf(current)
        val cycle = path.drop(cycleStart) :+ current
        if (cycle.size > 1) {
          cycles += cycle.sorted // Normalize for deduplication
        }
      } else if (!visited.contains(current)) {
        dependencies.getOrElse(current, Set.empty).foreach { dep =>
          findCycles(dep, path :+ current, visited + current)
        }
      }
    }

    dependencies.keys.foreach { pkg =>
      findCycles(pkg, Seq.empty, Set.empty)
    }

    cycles.toSeq.distinct.map { cycle =>
      CircularDependencyIssue(cycle, Seq.empty)
    }
  }

  /**
   * Find duplicated code patterns (simplified)
   */
  private def findDuplicatedPatterns(files: Map[String, Source]): Seq[DuplicatedPatternIssue] = {
    // Simplified: look for methods with similar structure
    val methodPatterns = files.flatMap { case (file, source) =>
      source.collect {
        case d @ Defn.Def(_, name, _, paramss, _, body) =>
          val pattern = normalizePattern(body)
          if (pattern.length > 50) { // Only consider non-trivial methods
            Some((pattern, name.value, file, d.pos.startLine))
          } else None
      }.flatten
    }

    // Group by pattern similarity
    methodPatterns.groupBy(_._1).filter(_._2.size > 1).flatMap { case (pattern, instances) =>
      if (instances.size >= 2) {
        Some(DuplicatedPatternIssue(
          pattern = instances.head._2, // Method name as pattern description
          locations = instances.map(i => (i._3, i._4)).toSeq,
          similarity = 1.0
        ))
      } else None
    }.toSeq
  }

  /**
   * Find naming inconsistencies
   */
  private def findNamingInconsistencies(exports: Seq[ExportedSymbol]): Seq[NamingInconsistencyIssue] = {
    // Group by base name (removing common suffixes)
    val baseSuffixes = Seq("Service", "Repository", "Controller", "Handler", "Manager", "Util", "Utils", "Helper")

    val grouped = exports
      .filter(exp => exp.kind == SymbolKind.Class || exp.kind == SymbolKind.Object)
      .groupBy { exp =>
        val name = exp.name
        val base = baseSuffixes.foldLeft(name) { (n, suffix) =>
          if (n.endsWith(suffix)) n.dropRight(suffix.length) else n
        }
        base.toLowerCase
      }
      .filter(_._2.size > 1)

    grouped.flatMap { case (base, symbols) =>
      val variants = symbols.map(s => (s.name, s.file))
      if (variants.map(_._1).distinct.size > 1) {
        Some(NamingInconsistencyIssue(
          pattern = base,
          variants = variants,
          suggestedConvention = variants.map(_._1).maxBy(_.length) // Suggest most complete name
        ))
      } else None
    }.toSeq
  }

  private def isPublic(mods: Seq[Mod]): Boolean = {
    !mods.exists {
      case Mod.Private(_) => true
      case Mod.Protected(_) => true
      case _ => false
    }
  }

  /**
   * Normalize code pattern for comparison
   */
  private def normalizePattern(tree: Tree): String = {
    // Remove variable names and literals, keep structure
    tree.syntax
      .replaceAll("\"[^\"]*\"", "\"STR\"")
      .replaceAll("\\b[0-9]+\\b", "NUM")
      .replaceAll("\\b[a-z][a-zA-Z0-9]*\\b", "id")
  }
}
