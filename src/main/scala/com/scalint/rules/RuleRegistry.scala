package com.scalint.rules

import com.scalint.core._

/**
 * Registry of all available lint rules
 */
object RuleRegistry {

  /**
   * All available rules
   */
  val allRules: Seq[Rule] = {
    StyleRules.all ++
    BugRules.all ++
    PerformanceRules.all ++
    SecurityRules.all ++
    ConcurrencyRules.all ++
    FunctionalRules.all
  }

  /**
   * Get rules by category
   */
  def byCategory(category: Category): Seq[Rule] = {
    allRules.filter(_.category == category)
  }

  /**
   * Get rules by severity
   */
  def bySeverity(severity: Severity): Seq[Rule] = {
    allRules.filter(_.severity == severity)
  }

  /**
   * Get a rule by ID
   */
  def byId(id: String): Option[Rule] = {
    allRules.find(_.id == id)
  }

  /**
   * Get a rule by name
   */
  def byName(name: String): Option[Rule] = {
    allRules.find(_.name == name)
  }

  /**
   * Get enabled rules based on configuration
   */
  def enabledRules(config: LintConfig): Seq[Rule] = {
    allRules.filter { rule =>
      config.isRuleEnabled(rule.id) &&
      config.enabledCategories.contains(rule.category) &&
      rule.severity >= config.minSeverity
    }
  }

  /**
   * Group rules by category
   */
  def groupedByCategory: Map[Category, Seq[Rule]] = {
    allRules.groupBy(_.category)
  }

  /**
   * Get rule count
   */
  def ruleCount: Int = allRules.size

  /**
   * Print rule documentation
   */
  def documentation: String = {
    val sb = new StringBuilder

    sb.append("# ScalaLint Rules\n\n")
    sb.append(s"Total rules: $ruleCount\n\n")

    Category.all.foreach { category =>
      val categoryRules = byCategory(category)
      if (categoryRules.nonEmpty) {
        sb.append(s"## ${category.name.capitalize} (${categoryRules.size} rules)\n\n")
        sb.append(s"${category.description}\n\n")

        categoryRules.sortBy(_.id).foreach { rule =>
          sb.append(s"### ${rule.id}: ${rule.name}\n\n")
          sb.append(s"**Severity:** ${rule.severity.name}\n\n")
          sb.append(s"${rule.description}\n\n")
          sb.append(s"${rule.explanation}\n\n")
        }
      }
    }

    sb.toString
  }

  /**
   * Get all rule IDs
   */
  val ruleIds: Set[String] = allRules.map(_.id).toSet

  /**
   * Get all rule names
   */
  val ruleNames: Set[String] = allRules.map(_.name).toSet

  /**
   * Validate rule ID or name
   */
  def isValidRuleIdentifier(identifier: String): Boolean = {
    ruleIds.contains(identifier) || ruleNames.contains(identifier)
  }
}
