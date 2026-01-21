// Use the locally built plugin
sys.props.get("plugin.version") match {
  case Some(v) => addSbtPlugin("com.scalint" % "sbt-scalint" % v)
  case _ => sys.error("The system property 'plugin.version' is not defined.")
}
