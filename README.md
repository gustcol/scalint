# ScalaLint

**A comprehensive static analysis tool for Scala**

*Like ShellCheck for shell scripts or ty for Python - but for Scala*

---

ScalaLint is a powerful, fast static analysis tool that helps you write better Scala code. It detects bugs, security vulnerabilities, performance issues, style violations, and promotes functional programming best practices.

## Features

- **140+ Built-in Rules** across 14 categories
- **Multiple Output Formats**: Text, JSON, Compact, GitHub Actions, Checkstyle XML, HTML, SARIF
- **Auto-Fix**: Automatic code fixes for many rules
- **Configuration File**: YAML-based configuration (.scalint.yaml)
- **Baseline Support**: Gradual adoption for legacy projects
- **Watch Mode**: Automatic re-analysis on file changes
- **Cross-File Analysis**: Detect project-wide issues
- **sbt Plugin**: Native sbt integration
- **Fast**: Uses Scalameta for efficient parsing
- **CI/CD Ready**: Native support for GitHub Actions and CI pipelines
- **Scala 2.12, 2.13, and Scala 3 Support**
- **Apache Spark Rules**: Detect common Spark anti-patterns
- **Delta Lake Rules**: Delta Lake best practices
- **Effect System Rules**: Cats Effect and ZIO patterns
- **Complexity Analysis**: Cyclomatic complexity and code metrics

## Installation

### From Source

```bash
# Clone the repository
git clone https://github.com/gustcol/scalint.git
cd scalint

# Build the project
sbt assembly

# The JAR will be at target/scala-2.13/scalint.jar
```

### sbt Plugin

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("com.scalint" % "sbt-scalint" % "0.1.0")
```

Enable in `build.sbt`:

```scala
enablePlugins(ScalintPlugin)

// Optional settings
scalintFailOnError := true
scalintFailOnWarning := false
scalintConfig := Some(file(".scalint.yaml"))
```

### Running

```bash
# Using Java directly
java -jar target/scala-2.13/scalint.jar [options] <paths>

# Or create an alias
alias scalint="java -jar /path/to/scalint.jar"

# Or using sbt plugin
sbt scalint
sbt scalintTest
sbt scalintAll
```

## Quick Start

```bash
# Analyze a single file
scalint MyFile.scala

# Analyze a directory
scalint src/main/scala

# Analyze multiple paths
scalint src/main/scala src/test/scala

# Read from stdin
cat MyFile.scala | scalint

# Output as JSON
scalint --format json src/

# Only show errors and warnings
scalint --severity warning src/

# List all available rules
scalint --list-rules

# Apply auto-fixes
scalint --fix src/

# Watch mode
scalint --watch src/

# Generate baseline
scalint --generate-baseline src/

# Generate HTML report
scalint --format html --output report.html src/
```

## Command Line Options

```
Usage: scalint [options] <path>...

  -h, --help               Show this help message
  -v, --version            Show version information
  -f, --format <format>    Output format: text, json, compact, github, checkstyle, html, sarif
  -o, --output <file>      Write output to file instead of stdout
  -e, --enable <rules>     Enable only these rules (comma-separated)
  -d, --disable <rules>    Disable these rules (comma-separated)
  -c, --category <cats>    Enable only these categories (comma-separated)
  -s, --severity <level>   Minimum severity: error, warning, info, hint
  --dialect <dialect>      Scala dialect: scala213, scala212, scala3, sbt
  -x, --exclude <patterns> Exclude files matching these glob patterns
  --no-colors              Disable colored output
  --no-suggestions         Don't show fix suggestions
  -w, --fail-on-warning    Exit with error code if warnings are found
  -q, --quiet              Only output issues, no summary
  --verbose                Show verbose output
  -l, --list-rules         List all available rules
  --config <file>          Load configuration from file (.scalint.yaml)

  # Auto-fix options
  --fix                    Apply automatic fixes
  --fix-dry-run            Show what fixes would be applied without applying

  # Baseline options
  --generate-baseline      Generate baseline file for current issues
  --baseline <file>        Use baseline file to filter known issues
  --clean-baseline         Remove stale entries from baseline

  # Watch mode
  --watch                  Watch for file changes and re-analyze

  # Cross-file analysis
  --cross-file             Enable cross-file analysis
```

## Configuration File

Create `.scalint.yaml` in your project root:

```yaml
# ScalaLint Configuration

# Enable/disable rules
rules:
  enabled:
    - S001
    - B001
    - SEC001
  disabled:
    - F006
    - P003

# Enable/disable categories
categories:
  enabled:
    - security
    - bug
    - spark
  disabled:
    - style

# Severity overrides
severity:
  S001: error      # Upgrade class naming to error
  P003: warning    # Downgrade view suggestion to warning

# File patterns
include:
  - "src/main/scala/**/*.scala"
  - "src/test/scala/**/*.scala"
exclude:
  - "**/target/**"
  - "**/generated/**"
  - "**/*.sc"

# General settings
minSeverity: info
failOnWarning: false
maxIssues: 0       # 0 = unlimited

# Scala dialect
dialect: scala213  # scala212, scala213, scala3, sbt

# Baseline
baseline: .scalint-baseline.json

# Report
outputFormat: text
outputFile: null
```

## Rule Categories

### Style (11 rules)
Code style and naming conventions.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| S001 | class-naming | warning | Class names should be in PascalCase |
| S002 | object-naming | warning | Object names should be in PascalCase |
| S003 | method-naming | warning | Method names should be in camelCase |
| S004 | variable-naming | warning | Variable names should be in camelCase |
| S005 | constant-naming | info | Constants should be in UPPER_SNAKE_CASE |
| S006 | avoid-return | warning | Avoid using explicit return statements |
| S007 | string-interpolation | info | Prefer string interpolation over concatenation |
| S008 | line-length | info | Lines should not exceed 120 characters |
| S009 | avoid-procedure-syntax | warning | Avoid procedure syntax |
| S010 | meaningful-param-names | info | Parameter names should be meaningful |
| S011 | avoid-wildcard-imports | info | Avoid wildcard imports |

### Bug Detection (18 rules)
Potential bugs and logical errors.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| B001 | avoid-null | warning | Avoid using null; use Option instead |
| B002 | avoid-option-get | warning | Avoid using .get on Option |
| B003 | avoid-head | warning | Avoid using .head on collections |
| B004 | avoid-last | warning | Avoid using .last on collections |
| B005 | avoid-throwing | info | Consider using Either or Try |
| B006 | unreachable-code | error | Detect unreachable code |
| B007 | non-exhaustive-match | warning | Pattern matching should be exhaustive |
| B008 | float-comparison | warning | Avoid comparing floats with == |
| B009 | var-in-case-class | warning | Avoid var in case classes |
| B010 | mutable-collection-api | warning | Avoid mutable collections in APIs |
| B011 | empty-catch-block | warning | Avoid empty catch blocks |
| B012 | boolean-comparison | info | Avoid comparing with Boolean literals |
| B013 | shadowing | warning | Avoid shadowing outer variables |
| B014 | equals-without-hashcode | error | Override both equals and hashCode together |
| B015 | unsafe-try-get | warning | Avoid using .get on Try |
| B016 | resource-leak | warning | Resources should be properly closed |
| B017 | return-in-lambda | error | Avoid return in lambda expressions |
| B018 | await-inside-future | error | Avoid Await inside Future blocks |

### Performance (15 rules)
Performance issues and inefficiencies.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| P001 | use-isempty | warning | Use isEmpty instead of size == 0 |
| P002 | multiple-traversals | info | Avoid multiple collection traversals |
| P003 | use-view | hint | Consider using view for lazy evaluation |
| P004 | string-concat-loop | warning | Avoid string concatenation in loops |
| P005 | avoid-indexed-foreach | info | Avoid indexed access in foreach |
| P006 | prefer-specific-collection | hint | Use specific collection types |
| P007 | regex-in-loop | warning | Avoid regex compilation in loops |
| P008 | use-exists-forall | info | Use exists/forall for Boolean results |
| P009 | unnecessary-object-creation | info | Avoid unnecessary object creation |
| P010 | hashcode-efficiency | info | Ensure hashCode efficiency |
| P011 | large-collection-literal | info | Large collection literals should use builders |
| P012 | inefficient-contains | info | Use Set for frequent contains checks |
| P013 | inefficient-sorting | hint | Use sortBy instead of sortWith for simple keys |
| P014 | groupby-mapvalues | hint | Use groupMapReduce instead of groupBy + mapValues |
| P015 | range-to-list | info | Avoid converting Range to List unnecessarily |

### Security (12 rules)
Security vulnerabilities.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| SEC001 | sql-injection | error | Potential SQL injection |
| SEC002 | command-injection | error | Potential command injection |
| SEC003 | path-traversal | warning | Potential path traversal |
| SEC004 | hardcoded-credentials | error | Hardcoded credentials detected |
| SEC005 | unsafe-deserialization | warning | Unsafe deserialization |
| SEC006 | weak-cryptography | warning | Weak cryptographic algorithm |
| SEC007 | insecure-random | warning | Use SecureRandom for security |
| SEC008 | sensitive-logging | warning | Avoid logging sensitive data |
| SEC009 | insecure-ssl | error | Insecure SSL/TLS configuration |
| SEC010 | xxe-vulnerability | error | XML External Entity (XXE) vulnerability |
| SEC011 | exposed-endpoint | warning | Sensitive endpoint without authentication |
| SEC012 | regex-dos | warning | Regex pattern vulnerable to ReDoS |

### Concurrency (8 rules)
Concurrency and thread safety issues.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| C001 | var-in-concurrent | warning | Avoid var in concurrent code |
| C002 | blocking-in-future | warning | Avoid blocking in Future |
| C003 | missing-execution-context | info | Consider custom ExecutionContext |
| C004 | synchronized-on-public | info | Avoid synchronized on public methods |
| C005 | double-checked-locking | error | Double-checked locking is broken |
| C006 | mutable-state-actor | info | Be careful with mutable state in Actors |
| C007 | promise-completion | warning | Promise should be completed once |
| C008 | lazy-init-race | warning | Manual lazy initialization race conditions |

### Functional Style (10 rules)
Functional programming best practices.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| F001 | prefer-immutable-collections | info | Prefer immutable collections |
| F002 | side-effects-in-map | warning | Avoid side effects in map/filter |
| F003 | prefer-pattern-matching | warning | Prefer pattern matching over casts |
| F004 | prefer-fold-reduce | info | Prefer fold/reduce over var+foreach |
| F005 | use-option | warning | Use Option instead of null checks |
| F006 | avoid-while-loops | hint | Consider functional alternatives |
| F007 | prefer-for-comprehension | hint | Use for-comprehension for nested flatMap |
| F008 | use-collect | info | Use collect instead of filter+map |
| F009 | avoid-any | warning | Avoid using Any in type annotations |
| F010 | use-case-class | hint | Consider case class for data classes |

### Apache Spark (15 rules)
Apache Spark best practices and anti-patterns.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| SPK001 | collect-in-loop | error | Avoid calling .collect() inside loops |
| SPK002 | broadcast-mutable | warning | Avoid broadcasting mutable data structures |
| SPK003 | prefer-dataframe | info | Consider DataFrame API instead of RDD |
| SPK004 | avoid-udf | warning | Prefer native Spark functions over UDFs |
| SPK005 | shuffle-warning | info | Detect expensive shuffle operations |
| SPK006 | cache-unpersist | info | Cached DataFrames should be unpersisted |
| SPK007 | spark-sql-injection | error | Potential SQL injection in Spark SQL |
| SPK008 | avoid-count-for-empty | warning | Avoid .count() to check empty |
| SPK009 | checkpoint-usage | info | Consider checkpointing for long lineages |
| SPK010 | broadcast-join-hint | info | Consider broadcast join for small tables |
| SPK011 | coalesce-vs-repartition | info | Use coalesce when reducing partitions |
| SPK012 | avoid-groupby-collect | warning | Avoid collecting to List in groupBy |
| SPK013 | data-skew-pattern | warning | Detect potential data skew |
| SPK014 | partition-count-check | info | Large partition counts may cause issues |
| SPK015 | avoid-foreach-collect | error | Avoid collect inside foreach |

### Delta Lake (6 rules)
Delta Lake best practices and anti-patterns.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| DELTA001 | merge-condition | warning | MERGE condition should include partition key |
| DELTA002 | vacuum-retention | warning | VACUUM retention should be >= 7 days |
| DELTA003 | zorder-cardinality | info | Z-ORDER columns should have appropriate cardinality |
| DELTA004 | partition-pruning | warning | Queries should leverage partition pruning |
| DELTA005 | optimize-frequency | info | Tables should be regularly optimized |
| DELTA006 | schema-evolution | warning | Schema evolution changes should be explicit |

### Effect System (8 rules)
Cats Effect and ZIO patterns.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| EFF001 | unsafe-run-sync | warning | Avoid unsafeRunSync outside main |
| EFF002 | blocking-in-io | warning | Use Blocker for blocking operations |
| EFF003 | future-mixed-with-io | error | Don't mix Future with effect types |
| EFF004 | try-in-effect | warning | Don't use try-catch in effect code |
| EFF005 | effect-in-collection | info | Effects in collections need traversal |
| EFF006 | resource-not-released | warning | Resource may not be properly released |
| EFF007 | fiber-leaked | warning | Fiber may not be properly joined/canceled |
| EFF008 | nested-flatmap | info | Consider for-comprehension for clarity |

### Complexity Analysis (10 rules)
Code complexity metrics.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| CX001 | method-length | warning | Method exceeds maximum line count |
| CX002 | parameter-count | warning | Method has too many parameters |
| CX003 | cyclomatic-complexity | warning | High cyclomatic complexity |
| CX004 | nesting-depth | warning | Excessive nesting depth |
| CX005 | class-size | info | Class has too many members |
| CX006 | file-size | info | File has too many lines |
| CX007 | case-class-arity | warning | Case class has too many fields |
| CX008 | boolean-parameter | info | Boolean parameters reduce readability |
| CX009 | magic-numbers | info | Use named constants instead of magic numbers |
| CX010 | feature-envy | hint | Method may belong in another class |

### API Security (10 rules)
HTTP/API security best practices.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| API001 | hardcoded-secret | error | Hardcoded secret in source code |
| API002 | sql-injection | error | SQL injection vulnerability |
| API003 | missing-input-validation | warning | Input not validated before use |
| API004 | unsafe-deserialization | error | Unsafe deserialization pattern |
| API005 | insecure-http | warning | Use HTTPS instead of HTTP |
| API006 | cors-wildcard | warning | CORS allows all origins |
| API007 | missing-rate-limiting | info | Endpoint may need rate limiting |
| API008 | sensitive-data-in-logs | warning | Sensitive data may be logged |
| API009 | missing-auth-check | warning | Endpoint missing authentication check |
| API010 | unsafe-redirect | warning | Redirect URL not validated |

### Test Quality (5 rules)
Test code quality and isolation.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| TST001 | test-in-production | error | Test utilities in production code |
| TST002 | mocking-not-isolated | warning | Mocking frameworks in production code |
| TST003 | test-without-assertion | warning | Test method without assertions |
| TST004 | flaky-test-pattern | warning | Pattern that causes flaky tests |
| TST005 | test-pollution | warning | Shared mutable state between tests |

### Scala 3 Migration (14 rules)
Scala 3 specific patterns and migration helpers.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| SC3001 | implicit-to-given | info | Consider using given/using instead of implicit |
| SC3002 | deprecated-scala2-syntax | warning | Deprecated Scala 2 syntax |
| SC3003 | wildcard-import-syntax | hint | Use * instead of _ for wildcard imports |
| SC3004 | type-lambda-syntax | hint | Consider Scala 3's type lambda syntax |
| SC3005 | optional-braces | hint | Mixed brace and indentation styles |
| SC3006 | enum-vs-sealed | hint | Consider Scala 3 enum for simple ADTs |
| SC3007 | export-clause | hint | Consider export clause for delegation |
| SC3008 | union-intersection-types | hint | Consider union/intersection types |
| SC3009 | context-function | info | Consider context functions for implicit parameters |
| SC3010 | opaque-type | info | Consider opaque types for type aliases |
| SC3011 | inline-modifier | hint | Consider inline for compile-time evaluation |
| SC3012 | match-type | hint | Consider match types for type-level computation |
| SC3013 | open-class | hint | Consider open modifier for extensible classes |
| SC3014 | main-annotation | hint | Use @main annotation for entry points |

### Cross-File Analysis (4 rules)
Project-wide analysis rules.

| ID | Name | Severity | Description |
|----|------|----------|-------------|
| XF001 | unused-export | info | Public symbol appears unused in project |
| XF002 | circular-dependency | warning | Circular dependency between packages |
| XF003 | duplicated-pattern | info | Similar code pattern found in multiple locations |
| XF004 | naming-inconsistency | hint | Inconsistent naming across modules |

## Auto-Fix

ScalaLint can automatically fix many issues:

```bash
# Apply all available fixes
scalint --fix src/

# Preview fixes without applying
scalint --fix-dry-run src/

# Fix only specific rules
scalint --fix --enable S001,S002 src/
```

Fixable rules include:
- Style rules (naming conventions, string interpolation)
- Simple bug patterns (Option.get, null checks)
- Import organization
- Basic refactorings

## Baseline for Legacy Projects

For existing projects with many issues, use baselines to adopt ScalaLint gradually:

```bash
# Generate baseline from current issues
scalint --generate-baseline src/

# This creates .scalint-baseline.json

# Future runs only report NEW issues
scalint --baseline .scalint-baseline.json src/

# Clean up fixed issues from baseline
scalint --clean-baseline --baseline .scalint-baseline.json src/
```

The baseline tracks issues by:
- File path (relative)
- Rule ID
- Line content hash (handles line number changes)

## Watch Mode

Automatically re-analyze on file changes:

```bash
scalint --watch src/

# With specific options
scalint --watch --severity warning --category spark src/
```

Features:
- Debouncing to avoid excessive re-runs
- Incremental analysis
- Clear terminal output with summary

## Cross-File Analysis

Enable project-wide analysis:

```bash
scalint --cross-file src/
```

Detects:
- Unused public symbols
- Circular package dependencies
- Duplicated code patterns
- Naming inconsistencies across modules

## HTML Reports

Generate interactive HTML reports:

```bash
scalint --format html --output report.html src/
```

Features:
- Summary dashboard with charts
- Filtering by severity and rule
- Code snippets with highlighting
- Issue explanations and suggestions

## sbt Plugin

### Tasks

```bash
sbt scalint              # Lint main sources
sbt scalintTest          # Lint test sources
sbt scalintAll           # Lint all sources
sbt scalintFix           # Apply auto-fixes
sbt scalintGenerateBaseline  # Generate baseline
sbt scalintCleanBaseline     # Clean stale baseline entries
sbt scalintWatch         # Watch mode
```

### Settings

```scala
// build.sbt
enablePlugins(ScalintPlugin)

// Configuration
scalintConfig := Some(file(".scalint.yaml"))
scalintFailOnError := true
scalintFailOnWarning := false
scalintExclude := Seq("**/generated/**")
scalintInclude := Seq("**/*.scala")
scalintRules := Seq.empty        // All rules
scalintDisabledRules := Seq("F006", "P003")
scalintCategories := Seq.empty   // All categories
scalintReportFormat := "console" // or json, html, sarif
scalintReportFile := Some(file("scalint-report.html"))
scalintBaseline := Some(file(".scalint-baseline.json"))
scalintMaxIssues := 0            // 0 = unlimited
scalintVerbose := false
```

## Output Formats

### Text (Default)

```bash
$ scalint src/main/scala/Example.scala

=== ScalaLint Analysis Report ===

src/main/scala/Example.scala
  5:3   [W] [S002] Object name 'user_service' should be in PascalCase
        Suggestion: Use PascalCase: UserService
  8:7   [E] [SEC004] Potential hardcoded credential in 'password'
        Suggestion: Use environment variables: sys.env.getOrElse("KEY", "")
  14:5  [W] [B001] Avoid using null; use Option[T] instead
        Suggestion: Use None for absent values, Some(value) for present values

--- Summary ---
Files analyzed: 1/1
Issues found: 1 errors, 2 warnings, 0 info, 0 hints

Analysis completed with errors.
```

### Compact Format

```bash
$ scalint --format compact src/
src/Example.scala:5:3: warning: [S002] Object name 'user_service' should be in PascalCase
src/Example.scala:8:7: error: [SEC004] Potential hardcoded credential in 'password'
src/Example.scala:14:5: warning: [B001] Avoid using null; use Option[T] instead
```

### JSON Format

```bash
$ scalint --format json src/
```

```json
{
  "totalFiles": 1,
  "analyzedFiles": 1,
  "skippedFiles": 0,
  "totalIssues": 3,
  "errorCount": 1,
  "warningCount": 2,
  "infoCount": 0,
  "hintCount": 0,
  "hasErrors": true,
  "files": [
    {
      "file": "src/Example.scala",
      "issues": [
        {
          "ruleId": "S002",
          "ruleName": "object-naming",
          "category": "style",
          "severity": "warning",
          "message": "Object name 'user_service' should be in PascalCase",
          "position": {
            "file": "src/Example.scala",
            "startLine": 5,
            "startColumn": 3,
            "endLine": 5,
            "endColumn": 15
          },
          "suggestion": "Use PascalCase: UserService",
          "explanation": "Object names should start with an uppercase letter...",
          "fix": {
            "description": "Rename to UserService",
            "replacement": "object UserService"
          }
        }
      ]
    }
  ]
}
```

### GitHub Actions Format

```bash
$ scalint --format github src/
::warning file=src/Example.scala,line=5,col=3,title=[S002] object-naming::Object name 'user_service' should be in PascalCase
::error file=src/Example.scala,line=8,col=7,title=[SEC004] hardcoded-credentials::Potential hardcoded credential in 'password'
```

### Checkstyle XML Format

```bash
$ scalint --format checkstyle src/
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="8.0">
  <file name="src/Example.scala">
    <error line="5" column="3" severity="warning"
           message="Object name 'user_service' should be in PascalCase"
           source="scalint.S002"/>
    <error line="8" column="7" severity="error"
           message="Potential hardcoded credential in 'password'"
           source="scalint.SEC004"/>
  </file>
</checkstyle>
```

### SARIF Format

```bash
$ scalint --format sarif --output results.sarif src/
```

SARIF (Static Analysis Results Interchange Format) is supported for integration with security tools and code scanning platforms like GitHub Advanced Security.

## Examples

### Filtering by Category

```bash
# Only security rules
scalint --category security src/

# Only bug and performance rules
scalint --category bug,performance src/

# Only Spark rules
scalint --category spark src/

# Only Delta Lake rules
scalint --category delta src/

# Only effect system rules
scalint --category effect src/

# Available categories: style, bug, performance, security, concurrency,
#                       functional, spark, delta, effect, complexity, api, test, scala3
```

### Filtering by Severity

```bash
# Only errors
scalint --severity error src/

# Errors and warnings
scalint --severity warning src/

# All issues including hints
scalint --severity hint src/
```

### Enable/Disable Specific Rules

```bash
# Only run specific rules
scalint --enable S001,S002,SEC004 src/

# Disable specific rules
scalint --disable B001,F006 src/
```

### Exclude Files

```bash
# Exclude test files
scalint --exclude "**/test/**" src/

# Exclude multiple patterns
scalint --exclude "**/test/**,**/generated/**" src/
```

## GitHub Actions Integration

```yaml
# .github/workflows/lint.yml
name: ScalaLint

on: [push, pull_request]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Download ScalaLint
        run: |
          # Download or build scalint.jar
          sbt assembly

      - name: Run ScalaLint
        run: |
          java -jar target/scala-2.13/scalint.jar \
            --format github \
            --severity warning \
            src/main/scala
```

## VS Code Integration

Create `.vscode/tasks.json`:

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "ScalaLint",
      "type": "shell",
      "command": "java -jar scalint.jar --format compact ${file}",
      "problemMatcher": {
        "pattern": {
          "regexp": "^(.+):(\\d+):(\\d+): (error|warning|info): (.+)$",
          "file": 1,
          "line": 2,
          "column": 3,
          "severity": 4,
          "message": 5
        }
      }
    }
  ]
}
```

## Best Practices

### 1. Start with Errors Only

```bash
scalint --severity error src/
```

Fix all errors first, then gradually lower the severity level.

### 2. Use Baselines for Legacy Projects

```bash
# Generate baseline
scalint --generate-baseline src/

# Commit .scalint-baseline.json to version control

# Future runs only report new issues
scalint --baseline .scalint-baseline.json src/
```

### 3. Prioritize Security Issues

```bash
scalint --category security,api src/
```

Security issues should be the highest priority.

### 4. Use in CI/CD

```bash
scalint --severity warning --fail-on-warning src/
```

### 5. Customize for Your Project

Create `.scalint.yaml` with your team's preferences:

```yaml
rules:
  disabled:
    - F006  # while loops are acceptable
    - P003  # views add complexity

severity:
  S001: error  # naming is important

categories:
  disabled:
    - style  # focus on bugs/security first
```

### 6. Use Auto-Fix Cautiously

```bash
# Preview fixes first
scalint --fix-dry-run src/

# Then apply
scalint --fix src/
```

### 7. Use Watch Mode During Development

```bash
scalint --watch src/main/scala
```

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success - no errors found |
| 1 | Errors found or analysis failed |

With `--fail-on-warning`, exit code 1 is also returned if warnings are found.

## Running Tests

```bash
# Run all tests
sbt test

# Run specific test suite
sbt "testOnly *StyleRulesSpec"
```

## Building

```bash
# Compile
sbt compile

# Run tests
sbt test

# Create fat JAR
sbt assembly

# The JAR will be at target/scala-2.13/scalint.jar

# Build sbt plugin
cd sbt-plugin
sbt publishLocal
```

## Project Structure

```
scalint/
  src/
    main/scala/com/scalint/
      cli/              # Command-line interface
        WatchMode.scala # Watch mode implementation
      core/             # Core models and analyzer
        Models.scala    # Issue, Severity, Category definitions
        Rule.scala      # Rule trait and auto-fix support
      config/           # Configuration
        ConfigLoader.scala  # YAML config parser
      parser/           # Scala parser wrapper
      reporter/         # Output formatters
        HtmlReporter.scala  # HTML report generator
      baseline/         # Baseline support
        BaselineManager.scala
      analysis/         # Cross-file analysis
        CrossFileAnalyzer.scala
      rules/            # Lint rules by category
        StyleRules.scala
        BugRules.scala
        PerformanceRules.scala
        SecurityRules.scala
        ConcurrencyRules.scala
        FunctionalRules.scala
        SparkRules.scala
        SparkRulesExtended.scala
        DeltaLakeRules.scala
        EffectSystemRules.scala
        ComplexityRules.scala
        ApiSecurityRules.scala
        TestRules.scala
        Scala3Rules.scala
        RuleRegistry.scala
    test/scala/com/scalint/
      rules/            # Test suites for rules
    test/resources/
      sample_good.scala
      sample_bad.scala

  sbt-plugin/           # sbt plugin
    src/main/scala/com/scalint/sbt/
      ScalintPlugin.scala
```

## Contributing

We welcome contributions! To add a new rule:

1. Choose the appropriate category in `src/main/scala/com/scalint/rules/`
2. Implement the `Rule` trait (or `FixableRule` for auto-fixable rules)
3. Register the rule in `RuleRegistry`
4. Add tests in the corresponding test file
5. Update the README with the new rule

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [Scalameta](https://scalameta.org/) - For the excellent Scala parsing library
- [ShellCheck](https://www.shellcheck.net/) - Inspiration for the project
- [ty](https://github.com/astral-sh/ty) - Inspiration for the project structure

---

Made for the Scala community
