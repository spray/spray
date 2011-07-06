import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  
  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
    val AkkaRepo = MavenRepository("Akka Repository", "http://akka.io/repository")
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._
  lazy val akkaModuleConfig = ModuleConfiguration("se.scalablesolutions.akka", AkkaRepo)

  // -------------------------------------------------------------------------------------------------------------------
  // Plugins
  // -------------------------------------------------------------------------------------------------------------------
  val akkaPlugin = "se.scalablesolutions.akka" % "akka-sbt-plugin" % "1.1.3"
  
}
