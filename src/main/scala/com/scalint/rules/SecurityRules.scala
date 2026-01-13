package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * Rule: SQL Injection detection
 */
object SqlInjectionRule extends Rule {
  val id = "SEC001"
  val name = "sql-injection"
  val description = "Potential SQL injection vulnerability"
  val category = Category.Security
  val severity = Severity.Error
  override val explanation = "Concatenating user input into SQL queries can lead to SQL injection attacks. Use parameterized queries instead."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect string interpolation in SQL-like contexts
      case t @ Term.Interpolate(Term.Name("sql"), _, args) if args.exists(_.isInstanceOf[Term.Name]) =>
        issue(
          "Potential SQL injection: avoid string interpolation with variables in SQL queries",
          t.pos,
          file,
          suggestion = Some("Use parameterized queries with placeholders (?, $1, etc.)")
        )
      // Detect string concatenation with SQL keywords
      case t @ Term.ApplyInfix(Lit.String(s), Term.Name("+"), _, _) if hasSqlKeyword(s) =>
        issue(
          "Potential SQL injection: string concatenation with SQL keywords",
          t.pos,
          file,
          suggestion = Some("Use parameterized queries instead of string concatenation")
        )
    }
  }

  private val sqlKeywords = Set(
    "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE",
    "WHERE", "FROM", "JOIN", "UNION", "ORDER BY", "GROUP BY"
  )

  private def hasSqlKeyword(s: String): Boolean = {
    val upper = s.toUpperCase
    sqlKeywords.exists(upper.contains)
  }
}

/**
 * Rule: Command Injection detection
 */
object CommandInjectionRule extends Rule {
  val id = "SEC002"
  val name = "command-injection"
  val description = "Potential command injection vulnerability"
  val category = Category.Security
  val severity = Severity.Error
  override val explanation = "Executing shell commands with user input can lead to command injection attacks."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect Runtime.exec with string interpolation
      case t @ Term.Apply(Term.Select(_, Term.Name("exec")), args)
        if args.exists(_.isInstanceOf[Term.Interpolate]) =>
        issue(
          "Potential command injection: avoid using string interpolation in shell commands",
          t.pos,
          file,
          suggestion = Some("Use ProcessBuilder with properly escaped arguments")
        )
      // Detect stringToProcess implicit
      case t @ Term.Select(interp: Term.Interpolate, Term.Name("!")) =>
        issue(
          "Potential command injection: string interpolation used in shell command",
          t.pos,
          file,
          suggestion = Some("Validate and escape user input, or use ProcessBuilder")
        )
    }
  }
}

/**
 * Rule: Path Traversal detection
 */
object PathTraversalRule extends Rule {
  val id = "SEC003"
  val name = "path-traversal"
  val description = "Potential path traversal vulnerability"
  val category = Category.Security
  val severity = Severity.Warning
  override val explanation = "Using user input in file paths without validation can lead to path traversal attacks."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect File/Path with string interpolation
      case t @ Term.New(Init(Type.Name("File"), _, argss))
        if argss.flatten.exists(_.isInstanceOf[Term.Interpolate]) =>
        issue(
          "Potential path traversal: user input in file path",
          t.pos,
          file,
          suggestion = Some("Validate user input and use canonical path resolution")
        )
      case t @ Term.Apply(Term.Select(Term.Name("Paths"), Term.Name("get")), args)
        if args.exists(_.isInstanceOf[Term.Interpolate]) =>
        issue(
          "Potential path traversal: user input in Paths.get",
          t.pos,
          file,
          suggestion = Some("Validate user input and check canonical path")
        )
    }
  }
}

/**
 * Rule: Hardcoded credentials detection
 */
object HardcodedCredentialsRule extends Rule {
  val id = "SEC004"
  val name = "hardcoded-credentials"
  val description = "Potential hardcoded credentials detected"
  val category = Category.Security
  val severity = Severity.Error
  override val explanation = "Hardcoding credentials in source code is a security risk. Use environment variables or secure configuration."

  private val sensitiveNames = Set(
    "password", "passwd", "pwd", "secret", "apikey", "api_key",
    "accesskey", "access_key", "token", "auth", "credential"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect suspicious variable names with string literals
      case d: Defn.Val =>
        d.pats.flatMap {
          case Pat.Var(name) if isSensitiveName(name.value) =>
            d.rhs match {
              case Lit.String(s) if s.nonEmpty && s.length > 3 =>
                Some(issue(
                  s"Potential hardcoded credential in '${name.value}'",
                  d.pos,
                  file,
                  suggestion = Some("Use environment variables: sys.env.getOrElse(\"KEY\", \"\")")
                ))
              case _ => None
            }
          case _ => None
        }
      case d: Defn.Var =>
        d.pats.flatMap {
          case Pat.Var(name) if isSensitiveName(name.value) =>
            d.rhs match {
              case Some(Lit.String(s)) if s.nonEmpty && s.length > 3 =>
                Some(issue(
                  s"Potential hardcoded credential in '${name.value}'",
                  d.pos,
                  file,
                  suggestion = Some("Use environment variables or secure configuration")
                ))
              case _ => None
            }
          case _ => None
        }
    }.flatten
  }

  private def isSensitiveName(name: String): Boolean = {
    val lower = name.toLowerCase
    sensitiveNames.exists(lower.contains)
  }
}

/**
 * Rule: Unsafe deserialization
 */
object UnsafeDeserializationRule extends Rule {
  val id = "SEC005"
  val name = "unsafe-deserialization"
  val description = "Potential unsafe deserialization"
  val category = Category.Security
  val severity = Severity.Warning
  override val explanation = "Deserializing untrusted data can lead to remote code execution vulnerabilities."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect ObjectInputStream usage
      case t @ Term.New(Init(Type.Name("ObjectInputStream"), _, _)) =>
        issue(
          "Unsafe deserialization: ObjectInputStream can execute arbitrary code",
          t.pos,
          file,
          suggestion = Some("Use a safe serialization format like JSON, and validate input")
        )
      // Detect Java serialization
      case t @ Term.Apply(Term.Select(_, Term.Name("readObject")), _) =>
        issue(
          "Unsafe deserialization: readObject can execute arbitrary code",
          t.pos,
          file,
          suggestion = Some("Implement look-ahead ObjectInputStream or use alternative serialization")
        )
    }
  }
}

/**
 * Rule: Weak cryptography detection
 */
object WeakCryptographyRule extends Rule {
  val id = "SEC006"
  val name = "weak-cryptography"
  val description = "Use of weak cryptographic algorithm"
  val category = Category.Security
  val severity = Severity.Warning
  override val explanation = "MD5, SHA1, and DES are considered weak. Use SHA-256 or stronger algorithms."

  private val weakAlgorithms = Set("MD5", "SHA1", "SHA-1", "DES", "RC4", "RC2")

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect MessageDigest.getInstance with weak algorithm
      case t @ Term.Apply(
            Term.Select(Term.Name("MessageDigest"), Term.Name("getInstance")),
            List(Lit.String(alg))) if weakAlgorithms.contains(alg.toUpperCase) =>
        issue(
          s"Weak cryptographic algorithm: $alg",
          t.pos,
          file,
          suggestion = Some("Use SHA-256, SHA-384, or SHA-512 instead")
        )
      // Detect Cipher.getInstance with weak algorithm
      case t @ Term.Apply(
            Term.Select(Term.Name("Cipher"), Term.Name("getInstance")),
            List(Lit.String(alg))) if isWeakCipher(alg) =>
        issue(
          s"Weak cipher algorithm: $alg",
          t.pos,
          file,
          suggestion = Some("Use AES with GCM mode: AES/GCM/NoPadding")
        )
    }
  }

  private def isWeakCipher(alg: String): Boolean = {
    val upper = alg.toUpperCase
    upper.contains("DES") || upper.contains("RC4") || upper.contains("ECB")
  }
}

/**
 * Rule: Insecure random number generation
 */
object InsecureRandomRule extends Rule {
  val id = "SEC007"
  val name = "insecure-random"
  val description = "Use SecureRandom for security-sensitive operations"
  val category = Category.Security
  val severity = Severity.Warning
  override val explanation = "scala.util.Random and java.util.Random are not cryptographically secure. Use java.security.SecureRandom for tokens, passwords, etc."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    // Check if there are security-sensitive contexts
    val hasSecurityContext = source.syntax.toLowerCase.contains("token") ||
      source.syntax.toLowerCase.contains("password") ||
      source.syntax.toLowerCase.contains("secret")

    if (hasSecurityContext) {
      source.collect {
        case t @ Term.New(Init(Type.Name("Random"), _, _)) =>
          issue(
            "Using Random in security-sensitive context; use SecureRandom",
            t.pos,
            file,
            suggestion = Some("Replace with java.security.SecureRandom")
          )
      }
    } else Seq.empty
  }
}

/**
 * Rule: Logging sensitive information
 */
object SensitiveLoggingRule extends Rule {
  val id = "SEC008"
  val name = "sensitive-logging"
  val description = "Avoid logging sensitive information"
  val category = Category.Security
  val severity = Severity.Warning
  override val explanation = "Logging passwords, tokens, or other sensitive data can expose them in log files."

  private val sensitivePatterns = Set(
    "password", "passwd", "token", "secret", "apikey", "api_key",
    "credential", "auth", "ssn", "credit"
  )

  private val logMethods = Set("info", "debug", "warn", "error", "trace", "log", "println")

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Term.Apply(Term.Select(_, Term.Name(logMethod)), args)
        if logMethods.contains(logMethod.toLowerCase) =>
        args.flatMap {
          case Term.Interpolate(_, _, exprs) =>
            exprs.collect {
              case Term.Name(name) if sensitivePatterns.exists(name.toLowerCase.contains) =>
                issue(
                  s"Potential sensitive data logged: $name",
                  t.pos,
                  file,
                  suggestion = Some("Avoid logging sensitive data; mask or redact if necessary")
                )
            }
          case Term.Name(name) if sensitivePatterns.exists(name.toLowerCase.contains) =>
            Seq(issue(
              s"Potential sensitive data logged: $name",
              t.pos,
              file,
              suggestion = Some("Avoid logging sensitive data; mask or redact if necessary")
            ))
          case _ => Seq.empty
        }
    }.flatten
  }
}

/**
 * Rule: Insecure SSL/TLS configuration
 */
object InsecureSslRule extends Rule {
  val id = "SEC009"
  val name = "insecure-ssl"
  val description = "Insecure SSL/TLS configuration detected"
  val category = Category.Security
  val severity = Severity.Error
  override val explanation = "Disabling SSL certificate validation or hostname verification " +
    "removes critical protections against man-in-the-middle attacks."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect TrustManager that accepts all certificates
      case t @ Defn.Class(_, name, _, _, Template(_, inits, _, stats))
        if inits.exists(_.tpe.syntax.contains("X509TrustManager")) =>
        val hasEmptyCheck = stats.exists {
          case Defn.Def(_, Term.Name(methodName), _, _, _, body)
            if Set("checkClientTrusted", "checkServerTrusted").contains(methodName) =>
            body match {
              case Term.Block(Nil) => true
              case Lit.Unit() => true
              case _ => false
            }
          case _ => false
        }
        if (hasEmptyCheck) {
          Seq(issue(
            s"TrustManager '${name.value}' accepts all certificates - insecure!",
            t.pos,
            file,
            suggestion = Some("Use default TrustManager or properly validate certificates")
          ))
        } else Seq.empty

      // Detect HostnameVerifier that always returns true
      case t @ Term.Function(_, Lit.Boolean(true)) =>
        val sourceText = source.syntax
        if (sourceText.contains("HostnameVerifier") || sourceText.contains("setDefaultHostnameVerifier")) {
          Seq(issue(
            "HostnameVerifier that always returns true disables hostname verification",
            t.pos,
            file,
            suggestion = Some("Use default HostnameVerifier or implement proper verification")
          ))
        } else Seq.empty

      // Detect setDefaultHostnameVerifier
      case t @ Term.Apply(Term.Select(_, Term.Name("setDefaultHostnameVerifier")), _) =>
        Seq(issue(
          "Overriding default HostnameVerifier - ensure proper validation",
          t.pos,
          file,
          suggestion = Some("Avoid disabling hostname verification in production")
        ))

      // Detect SSLContext with insecure init
      case t @ Term.Apply(Term.Select(Term.Name(ctx), Term.Name("init")), args)
        if ctx.toLowerCase.contains("ssl") =>
        val hasNullOrTrustAll = args.exists {
          case Lit.Null() => true
          case Term.Name(name) => name.toLowerCase.contains("trustall")
          case _ => false
        }
        if (hasNullOrTrustAll) {
          Seq(issue(
            "SSLContext initialized with null or trust-all parameters",
            t.pos,
            file,
            suggestion = Some("Use proper TrustManager and KeyManager")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * Rule: XXE (XML External Entity) vulnerability
 */
object XxeVulnerabilityRule extends Rule {
  val id = "SEC010"
  val name = "xxe-vulnerability"
  val description = "Potential XXE (XML External Entity) vulnerability"
  val category = Category.Security
  val severity = Severity.Error
  override val explanation = "XML parsers with default settings may process external entities, " +
    "leading to file disclosure, SSRF, or denial of service attacks."

  private val xmlParsers = Set(
    "DocumentBuilderFactory", "SAXParserFactory", "XMLInputFactory",
    "SAXReader", "XMLReader", "DocumentBuilder"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect XML parser factory without feature configuration
      case t @ Term.Apply(Term.Select(Term.Name(factory), Term.Name("newInstance")), _)
        if xmlParsers.contains(factory) =>
        Seq(issue(
          s"$factory without XXE protection - configure secure features",
          t.pos,
          file,
          suggestion = Some("Set feature: \"http://apache.org/xml/features/disallow-doctype-decl\" to true")
        ))

      // Detect XML.loadString without sanitation
      case t @ Term.Apply(Term.Select(Term.Name("XML"), Term.Name("loadString")), args)
        if args.exists(_.isInstanceOf[Term.Name]) =>
        Seq(issue(
          "XML.loadString with user input may be vulnerable to XXE",
          t.pos,
          file,
          suggestion = Some("Use a configured SAXParser or disable DTD processing")
        ))
    }.flatten
  }
}

/**
 * Rule: Exposed endpoints without authentication
 */
object ExposedEndpointRule extends Rule {
  val id = "SEC011"
  val name = "exposed-endpoint"
  val description = "HTTP endpoint may lack authentication"
  val category = Category.Security
  val severity = Severity.Warning
  override val explanation = "HTTP endpoints should have proper authentication and authorization checks."

  private val httpAnnotations = Set("GET", "POST", "PUT", "DELETE", "PATCH")
  private val authKeywords = Set("auth", "authenticate", "authorized", "permission", "token", "jwt", "session")

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect route definitions without auth
      case t @ Defn.Def(mods, name, _, paramss, _, body) =>
        val hasHttpAnnotation = mods.exists {
          case Mod.Annot(Init(Type.Name(annot), _, _)) => httpAnnotations.contains(annot)
          case _ => false
        }

        val hasAuthCheck = {
          var found = false
          body.traverse {
            case Term.Name(n) if authKeywords.exists(n.toLowerCase.contains) => found = true
            case Term.Select(_, Term.Name(n)) if authKeywords.exists(n.toLowerCase.contains) => found = true
            case _ =>
          }
          found
        }

        if (hasHttpAnnotation && !hasAuthCheck) {
          Seq(issue(
            s"HTTP endpoint '${name.value}' may lack authentication",
            t.pos,
            file,
            suggestion = Some("Add authentication/authorization checks")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * Rule: Regex DoS (ReDoS)
 */
object RegexDosRule extends Rule {
  val id = "SEC012"
  val name = "regex-dos"
  val description = "Regex pattern may be vulnerable to ReDoS"
  val category = Category.Security
  val severity = Severity.Warning
  override val explanation = "Certain regex patterns with nested quantifiers can cause catastrophic backtracking, " +
    "leading to denial of service when processing malicious input."

  // Simplified check for potentially dangerous patterns
  private val dangerousPatterns = Seq(
    """\(\.\*\)\+""".r,     // (.*)+
    """\(\.\+\)\+""".r,     // (.+)+
    """\([^)]*\+[^)]*\)\+""".r,  // Nested + in groups
    """\([^)]*\*[^)]*\)\*""".r   // Nested * in groups
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect regex patterns
      case t @ Lit.String(pattern) if pattern.contains("(") && (pattern.contains("+") || pattern.contains("*")) =>
        val sourceContext = source.syntax
        val isRegex = sourceContext.contains(".r") || sourceContext.contains("Regex") || sourceContext.contains("Pattern")

        if (isRegex && dangerousPatterns.exists(_.findFirstIn(pattern).isDefined)) {
          Seq(issue(
            "Regex pattern may be vulnerable to ReDoS (catastrophic backtracking)",
            t.pos,
            file,
            suggestion = Some("Avoid nested quantifiers like (.*)+, use possessive quantifiers or atomic groups")
          ))
        } else Seq.empty
    }.flatten
  }
}

/**
 * All security rules
 */
object SecurityRules {
  val all: Seq[Rule] = Seq(
    SqlInjectionRule,
    CommandInjectionRule,
    PathTraversalRule,
    HardcodedCredentialsRule,
    UnsafeDeserializationRule,
    WeakCryptographyRule,
    InsecureRandomRule,
    SensitiveLoggingRule,
    InsecureSslRule,
    XxeVulnerabilityRule,
    ExposedEndpointRule,
    RegexDosRule
  )
}
