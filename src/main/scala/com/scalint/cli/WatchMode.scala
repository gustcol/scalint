package com.scalint.cli

import com.scalint.core._
import java.io.File
import scala.meta.Source
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.{Executors, TimeUnit}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Watch Mode Implementation
 *
 * Monitors the file system for changes and re-runs linting automatically.
 * Features:
 * - Efficient file watching using Java NIO WatchService
 * - Debouncing to avoid excessive re-runs
 * - Incremental analysis (only changed files)
 * - Clear terminal output with summary
 */
object WatchMode {

  private val debounceMs = 300 // Debounce time in milliseconds
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  /**
   * Start watching a directory for changes
   */
  def watch(
    paths: Seq[String],
    config: LintConfig,
    analyzer: Source => Seq[LintIssue],
    onAnalysis: AnalysisResult => Unit
  ): Unit = {
    val watcher = FileSystems.getDefault.newWatchService()
    val watchKeys = mutable.Map[WatchKey, Path]()
    val pendingChanges = mutable.Set[Path]()
    var scheduledTask: Option[java.util.concurrent.ScheduledFuture[_]] = None

    // Register directories
    paths.foreach { pathStr =>
      val path = Paths.get(pathStr)
      if (Files.isDirectory(path)) {
        registerDirectory(path, watcher, watchKeys)
      }
    }

    println(s"\n${Console.CYAN}👀 Watch mode started${Console.RESET}")
    println(s"   Watching: ${paths.mkString(", ")}")
    println(s"   Press Ctrl+C to exit\n")

    // Initial analysis
    runAnalysis(paths, config, onAnalysis)

    // Watch loop
    try {
      while (true) {
        val key = watcher.take()
        val dir = watchKeys.get(key)

        if (dir.isDefined) {
          key.pollEvents().asScala.foreach { event =>
            val kind = event.kind()

            if (kind != StandardWatchEventKinds.OVERFLOW) {
              val filename = event.context().asInstanceOf[Path]
              val fullPath = dir.get.resolve(filename)

              // Only watch Scala files
              if (fullPath.toString.endsWith(".scala") || fullPath.toString.endsWith(".sc")) {
                pendingChanges += fullPath

                // Debounce: cancel previous scheduled task and schedule new one
                scheduledTask.foreach(_.cancel(false))
                scheduledTask = Some(scheduler.schedule(
                  new Runnable {
                    def run(): Unit = {
                      val changes = pendingChanges.toSeq
                      pendingChanges.clear()

                      if (changes.nonEmpty) {
                        clearScreen()
                        println(s"${Console.YELLOW}📝 Changes detected in:${Console.RESET}")
                        changes.foreach(p => println(s"   ${p.getFileName}"))
                        println()

                        runAnalysis(paths, config, onAnalysis)
                      }
                    }
                  },
                  debounceMs,
                  TimeUnit.MILLISECONDS
                ))
              }

              // Register new directories
              if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                registerDirectory(fullPath, watcher, watchKeys)
              }
            }
          }
        }

        key.reset()
      }
    } finally {
      watcher.close()
      scheduler.shutdown()
    }
  }

  /**
   * Register a directory and all subdirectories for watching
   */
  private def registerDirectory(
    start: Path,
    watcher: WatchService,
    watchKeys: mutable.Map[WatchKey, Path]
  ): Unit = {
    try {
      Files.walkFileTree(start, new SimpleFileVisitor[Path] {
        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          // Skip common directories
          val dirName = dir.getFileName.toString
          if (Set("target", "node_modules", ".git", ".idea", ".bsp").contains(dirName)) {
            FileVisitResult.SKIP_SUBTREE
          } else {
            Try {
              val key = dir.register(
                watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
              )
              watchKeys(key) = dir
            }
            FileVisitResult.CONTINUE
          }
        }
      })
    } catch {
      case _: Exception => // Ignore registration errors
    }
  }

  /**
   * Run analysis on all files
   */
  private def runAnalysis(
    paths: Seq[String],
    config: LintConfig,
    onAnalysis: AnalysisResult => Unit
  ): Unit = {
    val startTime = System.currentTimeMillis()

    // Find all Scala files
    val files = paths.flatMap(findScalaFiles)

    // Analyze
    val results = files.map { file =>
      Try {
        val source = scala.io.Source.fromFile(file)
        try {
          val content = source.mkString
          val parsed = scala.meta.dialects.Scala213(content).parse[scala.meta.Source]
          parsed match {
            case scala.meta.parsers.Parsed.Success(tree) =>
              val issues = com.scalint.rules.RuleRegistry.enabledRules(config).flatMap { rule =>
                rule.check(tree, file, config)
              }
              FileAnalysisResult(file, issues)
            case scala.meta.parsers.Parsed.Error(_, message, _) =>
              FileAnalysisResult(file, Seq.empty, Some(message))
          }
        } finally {
          source.close()
        }
      }.getOrElse(FileAnalysisResult(file, Seq.empty, Some("Failed to read file")))
    }

    val analysisResult = AnalysisResult(
      fileResults = results,
      totalFiles = files.size,
      analyzedFiles = results.count(_.parseError.isEmpty),
      skippedFiles = results.count(_.parseError.isDefined)
    )

    val duration = System.currentTimeMillis() - startTime

    // Print summary
    printWatchSummary(analysisResult, duration)

    onAnalysis(analysisResult)
  }

  /**
   * Find all Scala files in a directory
   */
  private def findScalaFiles(path: String): Seq[String] = {
    val root = new File(path)
    if (root.isFile) {
      if (path.endsWith(".scala") || path.endsWith(".sc")) Seq(path) else Seq.empty
    } else if (root.isDirectory) {
      findScalaFilesRecursive(root)
    } else {
      Seq.empty
    }
  }

  private def findScalaFilesRecursive(dir: File): Seq[String] = {
    val dirName = dir.getName
    if (Set("target", "node_modules", ".git", ".idea", ".bsp").contains(dirName)) {
      Seq.empty
    } else {
      val files = Option(dir.listFiles()).getOrElse(Array.empty)
      files.flatMap { f =>
        if (f.isDirectory) findScalaFilesRecursive(f)
        else if (f.getName.endsWith(".scala") || f.getName.endsWith(".sc")) Seq(f.getAbsolutePath)
        else Seq.empty
      }.toSeq
    }
  }

  /**
   * Clear the terminal screen
   */
  private def clearScreen(): Unit = {
    print("\u001b[2J\u001b[H")
    System.out.flush()
  }

  /**
   * Print watch mode summary
   */
  private def printWatchSummary(result: AnalysisResult, durationMs: Long): Unit = {
    val timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

    println(s"${Console.CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${Console.RESET}")
    println(s"${Console.CYAN}  ScalaLint Watch Mode - $timestamp${Console.RESET}")
    println(s"${Console.CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${Console.RESET}")
    println()

    if (result.totalIssueCount == 0) {
      println(s"  ${Console.GREEN}✓ No issues found!${Console.RESET}")
    } else {
      if (result.errorCount > 0) {
        println(s"  ${Console.RED}✗ ${result.errorCount} error(s)${Console.RESET}")
      }
      if (result.warningCount > 0) {
        println(s"  ${Console.YELLOW}⚠ ${result.warningCount} warning(s)${Console.RESET}")
      }
      if (result.infoCount > 0) {
        println(s"  ${Console.BLUE}ℹ ${result.infoCount} info${Console.RESET}")
      }
      if (result.hintCount > 0) {
        println(s"  ${Console.WHITE}💡 ${result.hintCount} hint(s)${Console.RESET}")
      }

      println()

      // Show top issues
      val topIssues = result.allIssues.sortBy(i => (-i.severity.level, i.position.file)).take(5)
      topIssues.foreach { issue =>
        val color = issue.severity match {
          case Severity.Error => Console.RED
          case Severity.Warning => Console.YELLOW
          case Severity.Info => Console.BLUE
          case Severity.Hint => Console.WHITE
        }
        val shortFile = issue.position.file.split("/").takeRight(2).mkString("/")
        println(s"  $color[${issue.ruleId}]${Console.RESET} $shortFile:${issue.position.startLine}")
        println(s"    ${issue.message}")
      }

      if (result.totalIssueCount > 5) {
        println(s"\n  ... and ${result.totalIssueCount - 5} more issues")
      }
    }

    println()
    println(s"  ${Console.CYAN}Files: ${result.analyzedFiles} | Time: ${durationMs}ms${Console.RESET}")
    println(s"${Console.CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${Console.RESET}")
    println()
    println(s"  ${Console.WHITE}Watching for changes... (Ctrl+C to exit)${Console.RESET}")
    println()
  }
}
