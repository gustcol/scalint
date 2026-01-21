package com.scalint.rules

import com.scalint.core._
import scala.meta._

/**
 * HTTP/API Security rules for detecting common web application vulnerabilities
 */

/**
 * Rule API001: Hardcoded secrets in code
 */
object HardcodedSecretsRule extends Rule {
  val id = "API001"
  val name = "hardcoded-secrets"
  val description = "Secrets should not be hardcoded in source code"
  val category = Category.Api
  val severity = Severity.Error
  override val explanation = "Hardcoded secrets (API keys, passwords, tokens) in source code are a major " +
    "security risk. They can be exposed through version control, logs, or decompilation. " +
    "Use environment variables, secret management systems, or configuration files (not in VCS)."

  private val secretPatterns = Seq(
    "password" -> """(?i)(password|passwd|pwd)\s*=\s*["'][^"']+["']""".r,
    "api_key" -> """(?i)(api[-_]?key|apikey)\s*=\s*["'][^"']+["']""".r,
    "secret" -> """(?i)(secret|token)\s*=\s*["'][^"']{10,}["']""".r,
    "aws" -> """(?i)(aws[-_]?access|aws[-_]?secret)\s*=\s*["'][^"']+["']""".r,
    "connection_string" -> """(?i)jdbc:[^"'\s]+password=[^"'\s]+""".r
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    val sourceText = source.syntax

    secretPatterns.flatMap { case (secretType, pattern) =>
      pattern.findFirstMatchIn(sourceText).map { m =>
        LintIssue(
          ruleId = id,
          ruleName = name,
          category = category,
          severity = severity,
          message = s"Possible hardcoded $secretType detected",
          position = SourcePosition(file, 1, 1, 1, 1),
          suggestion = Some("Use environment variables: sys.env.get(\"SECRET_KEY\")"),
          explanation = Some(explanation)
        )
      }
    }
  }
}

/**
 * Rule API002: SQL injection vulnerability (API context)
 */
object ApiSqlInjectionRule extends Rule {
  val id = "API002"
  val name = "api-sql-injection"
  val description = "Use parameterized queries instead of string concatenation"
  val category = Category.Api
  val severity = Severity.Error
  override val explanation = "SQL injection occurs when user input is directly concatenated into SQL queries. " +
    "Always use parameterized queries or prepared statements. Never build SQL strings from user input."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect string interpolation with SQL keywords
      case t @ Term.Interpolate(Term.Name("s"), parts, args) if args.nonEmpty =>
        val sql = parts.map(_.syntax).mkString
        if (sql.toLowerCase.matches(""".*\b(select|insert|update|delete|where|from)\b.*""")) {
          Seq(issue(
            "SQL query with string interpolation - vulnerable to SQL injection",
            t.pos,
            file,
            suggestion = Some("Use parameterized queries: statement.setString(1, userInput)")
          ))
        } else {
          Seq.empty
        }

      // Detect executeQuery/executeUpdate with concatenated strings
      case t @ Term.Apply(
            Term.Select(_, Term.Name(method)),
            List(Term.ApplyInfix(_, Term.Name("+"), _, _)))
        if Set("executeQuery", "executeUpdate", "execute").contains(method) =>
        Seq(issue(
          s"$method with string concatenation - SQL injection risk",
          t.pos,
          file,
          suggestion = Some("Use PreparedStatement with parameterized queries")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule API003: Missing input validation
 */
object MissingInputValidationRule extends Rule {
  val id = "API003"
  val name = "missing-input-validation"
  val description = "API endpoints should validate input"
  val category = Category.Api
  val severity = Severity.Warning
  override val explanation = "All external input (request parameters, body, headers) should be validated " +
    "before processing. Unvalidated input can lead to security vulnerabilities and unexpected behavior."

  private val httpMethods = Set("get", "post", "put", "delete", "patch")

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect route definitions (various HTTP frameworks)
      case t @ Term.Apply(Term.Name(method), List(_, handler)) if httpMethods.contains(method.toLowerCase) =>
        val hasValidation = handler.collect {
          case Term.Name(name) if name.toLowerCase.contains("valid") => true
          case Term.Apply(Term.Name(name), _) if name.toLowerCase.contains("valid") => true
          case Term.Match(_, _) => true // Pattern matching as validation
          case _ => false
        }.contains(true)

        if (!hasValidation) {
          Seq(issue(
            s"HTTP $method handler without apparent input validation",
            t.pos,
            file,
            suggestion = Some("Add input validation before processing the request")
          ))
        } else {
          Seq.empty
        }

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule API004: Unsafe deserialization (API context)
 */
object ApiUnsafeDeserializationRule extends Rule {
  val id = "API004"
  val name = "api-unsafe-deserialization"
  val description = "Avoid unsafe deserialization of untrusted data"
  val category = Category.Api
  val severity = Severity.Error
  override val explanation = "Deserializing untrusted data can lead to remote code execution. " +
    "Avoid Java serialization for external data. Use safe formats like JSON with schema validation."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect ObjectInputStream usage
      case t @ Term.New(Init(Type.Name("ObjectInputStream"), _, _)) =>
        Seq(issue(
          "ObjectInputStream deserializes arbitrary objects - RCE vulnerability",
          t.pos,
          file,
          suggestion = Some("Use JSON/Protobuf with schema validation instead of Java serialization")
        ))

      // Detect readObject calls
      case t @ Term.Apply(Term.Select(_, Term.Name("readObject")), _) =>
        Seq(issue(
          "readObject() can execute arbitrary code during deserialization",
          t.pos,
          file,
          suggestion = Some("Validate object type before deserializing; prefer JSON/Protobuf")
        ))

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule API005: Missing HTTPS enforcement
 */
object MissingHttpsRule extends Rule {
  val id = "API005"
  val name = "missing-https"
  val description = "HTTP URLs should use HTTPS for security"
  val category = Category.Api
  val severity = Severity.Warning
  override val explanation = "HTTP traffic is unencrypted and can be intercepted. " +
    "Always use HTTPS for API calls, especially those involving authentication or sensitive data."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Lit.String(url) if url.startsWith("http://") && !url.contains("localhost") && !url.contains("127.0.0.1") =>
        Seq(issue(
          "HTTP URL detected - use HTTPS for encrypted communication",
          t.pos,
          file,
          suggestion = Some("Change http:// to https://")
        ))
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule API006: CORS wildcard
 */
object CorsWildcardRule extends Rule {
  val id = "API006"
  val name = "cors-wildcard"
  val description = "CORS wildcard (*) allows any origin - security risk"
  val category = Category.Api
  val severity = Severity.Warning
  override val explanation = "Access-Control-Allow-Origin: * allows any website to make requests to your API. " +
    "This can enable CSRF attacks. Specify allowed origins explicitly."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      case t @ Lit.String(value) if value == "*" =>
        // Check context to see if it's CORS related
        t.parent match {
          case Some(Term.Apply(Term.Select(_, Term.Name(name)), _))
            if name.toLowerCase.contains("origin") || name.toLowerCase.contains("cors") =>
            Seq(issue(
              "CORS wildcard (*) allows any origin - restricts to trusted domains",
              t.pos,
              file,
              suggestion = Some("Specify allowed origins: Access-Control-Allow-Origin: https://trusted-domain.com")
            ))
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule API007: Missing rate limiting
 */
object MissingRateLimitRule extends Rule {
  val id = "API007"
  val name = "missing-rate-limit"
  val description = "API endpoints should implement rate limiting"
  val category = Category.Api
  val severity = Severity.Info
  override val explanation = "Rate limiting prevents abuse and DoS attacks. Without it, attackers can " +
    "overwhelm your API with requests. Implement rate limiting at the application or infrastructure level."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    var hasHttpRoutes = false
    var hasRateLimiting = false

    source.traverse {
      // Check for HTTP framework usage
      case Term.Name(name) if Set("Route", "HttpRoutes", "routes", "endpoints").contains(name) =>
        hasHttpRoutes = true
      // Check for rate limiting
      case Term.Name(name) if name.toLowerCase.contains("ratelimit") || name.toLowerCase.contains("throttle") =>
        hasRateLimiting = true
      case Lit.String(s) if s.toLowerCase.contains("rate") && s.toLowerCase.contains("limit") =>
        hasRateLimiting = true
      case _ =>
    }

    if (hasHttpRoutes && !hasRateLimiting) {
      Seq(LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = "HTTP routes without apparent rate limiting",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Implement rate limiting to prevent abuse"),
        explanation = Some(explanation)
      ))
    } else {
      Seq.empty
    }
  }
}

/**
 * Rule API008: Sensitive data in logs
 */
object SensitiveDataInLogsRule extends Rule {
  val id = "API008"
  val name = "sensitive-data-logs"
  val description = "Avoid logging sensitive data"
  val category = Category.Api
  val severity = Severity.Warning
  override val explanation = "Logging sensitive data (passwords, tokens, PII) creates security risks. " +
    "Logs are often stored insecurely and accessed by many people. Mask or omit sensitive data in logs."

  private val sensitivePatterns = Set(
    "password", "passwd", "pwd", "secret", "token", "apikey", "api_key",
    "ssn", "credit_card", "creditcard", "cvv", "pin"
  )

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Check log statements
      case t @ Term.Apply(Term.Select(_, Term.Name(method)), args)
        if Set("info", "debug", "warn", "error", "trace", "log").contains(method) =>

        args.flatMap {
          case Term.Interpolate(_, parts, interpolatedArgs) =>
            val template = parts.map(_.syntax).mkString.toLowerCase
            val argNames = interpolatedArgs.collect {
              case Term.Name(name) => name.toLowerCase
            }

            val foundSensitive = sensitivePatterns.filter(pattern =>
              template.contains(pattern) || argNames.exists(_.contains(pattern))
            )

            if (foundSensitive.nonEmpty) {
              Seq(issue(
                s"Potentially sensitive data in log: ${foundSensitive.mkString(", ")}",
                t.pos,
                file,
                suggestion = Some("Mask or remove sensitive data from logs")
              ))
            } else {
              Seq.empty
            }

          case _ => Seq.empty
        }

      case _ => Seq.empty
    }.flatten
  }
}

/**
 * Rule API009: Missing authentication check
 */
object MissingAuthCheckRule extends Rule {
  val id = "API009"
  val name = "missing-auth-check"
  val description = "API endpoints should verify authentication"
  val category = Category.Api
  val severity = Severity.Warning
  override val explanation = "Every API endpoint that handles sensitive data should verify the user is " +
    "authenticated. Missing authentication checks can lead to unauthorized access."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    var hasRoutes = false
    var hasAuthCheck = false

    source.traverse {
      // Look for route definitions
      case Term.Name(name) if Set("routes", "endpoints", "HttpRoutes").contains(name) =>
        hasRoutes = true
      // Look for authentication
      case Term.Name(name) if name.toLowerCase.contains("auth") || name.toLowerCase.contains("authenticate") =>
        hasAuthCheck = true
      case Lit.String(s) if s.toLowerCase.contains("authorization") =>
        hasAuthCheck = true
      case _ =>
    }

    if (hasRoutes && !hasAuthCheck) {
      Seq(LintIssue(
        ruleId = id,
        ruleName = name,
        category = category,
        severity = severity,
        message = "HTTP routes without apparent authentication check",
        position = SourcePosition(file, 1, 1, 1, 1),
        suggestion = Some("Add authentication middleware or verify auth in each endpoint"),
        explanation = Some(explanation)
      ))
    } else {
      Seq.empty
    }
  }
}

/**
 * Rule API010: Unsafe redirect
 */
object UnsafeRedirectRule extends Rule {
  val id = "API010"
  val name = "unsafe-redirect"
  val description = "Validate redirect URLs to prevent open redirect vulnerabilities"
  val category = Category.Api
  val severity = Severity.Warning
  override val explanation = "Open redirect vulnerabilities occur when user input controls redirect URLs. " +
    "Attackers can redirect users to malicious sites. Always validate redirect destinations."

  def check(source: Source, file: String, config: LintConfig): Seq[LintIssue] = {
    source.collect {
      // Detect redirect with user-controlled URL
      case t @ Term.Apply(Term.Select(_, Term.Name(method)), List(arg))
        if Set("redirect", "Redirect", "seeOther", "movedPermanently").contains(method) =>
        arg match {
          case Term.Name(_) =>
            Seq(issue(
              "Redirect with potentially user-controlled URL - validate against allowlist",
              t.pos,
              file,
              suggestion = Some("Validate redirect URL against a list of allowed destinations")
            ))
          case Term.Select(_, _) =>
            Seq(issue(
              "Redirect URL from variable - ensure it's validated",
              t.pos,
              file,
              suggestion = Some("Use an allowlist of valid redirect destinations")
            ))
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }.flatten
  }
}

/**
 * All API Security rules
 */
object ApiSecurityRules {
  val all: Seq[Rule] = Seq(
    HardcodedSecretsRule,
    ApiSqlInjectionRule,
    MissingInputValidationRule,
    ApiUnsafeDeserializationRule,
    MissingHttpsRule,
    CorsWildcardRule,
    MissingRateLimitRule,
    SensitiveDataInLogsRule,
    MissingAuthCheckRule,
    UnsafeRedirectRule
  )
}
