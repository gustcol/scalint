# ScalaLint

**A comprehensive static analysis tool for Scala**

*Like ShellCheck for shell scripts or ty for Python - but for Scala*

---

ScalaLint is a powerful, fast static analysis tool that helps you write better Scala code. It detects bugs, security vulnerabilities, performance issues, style violations, and promotes functional programming best practices.

## Features

- **60 Built-in Rules** across 8 categories
- **Multiple Output Formats**: Text (colored), JSON, Compact, GitHub Actions, Checkstyle XML
- **Configurable**: Enable/disable rules, set severity levels, exclude files
- **Fast**: Uses Scalameta for efficient parsing
- **CI/CD Ready**: Native support for GitHub Actions and CI pipelines
- **Scala 2.12, 2.13, and Scala 3 Support**

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

### Running

```bash
# Using Java directly
java -jar target/scala-2.13/scalint.jar [options] <paths>

# Or create an alias
alias scalint="java -jar /path/to/scalint.jar"
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
```

## Command Line Options

```
Usage: scalint [options] <path>...

  -h, --help               Show this help message
  -v, --version            Show version information
  -f, --format <format>    Output format: text, json, compact, github, checkstyle
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
  --config <file>          Load configuration from file
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

### Bug Detection (13 rules)
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

### Performance (10 rules)
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

### Security (8 rules)
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
          "explanation": "Object names should start with an uppercase letter..."
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

## Examples

### Filtering by Category

```bash
# Only security rules
scalint --category security src/

# Only bug and performance rules
scalint --category bug,performance src/

# Available categories: style, bug, performance, security, concurrency, functional
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

### 2. Prioritize Security Issues

```bash
scalint --category security src/
```

Security issues should be the highest priority.

### 3. Use in CI/CD

```bash
scalint --severity warning --fail-on-warning src/
```

### 4. Customize for Your Project

Disable rules that don't fit your coding style:

```bash
scalint --disable F006,P003 src/
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
```

## Project Structure

```
scalint/
  src/
    main/scala/com/scalint/
      cli/          # Command-line interface
      core/         # Core models and analyzer
      parser/       # Scala parser wrapper
      reporter/     # Output formatters
      rules/        # Lint rules by category
    test/scala/com/scalint/
      rules/        # Test suites for rules
    test/resources/
      sample_good.scala  # Example of good code
      sample_bad.scala   # Example of problematic code
```

## Contributing

We welcome contributions! To add a new rule:

1. Choose the appropriate category in `src/main/scala/com/scalint/rules/`
2. Implement the `Rule` trait
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
