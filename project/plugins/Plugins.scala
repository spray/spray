import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  
  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
    lazy val sbtIdeaRepo = MavenRepository("sbt-idea-repo", "http://mpeltonen.github.com/maven/")
    lazy val akkaRepo    = MavenRepository("Akka Repository", "http://akka.io/repository")
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._
  lazy val sbtIdeaConfig              = ModuleConfiguration("com.github.mpeltonen", sbtIdeaRepo)
  lazy val akkaModuleConfig           = ModuleConfiguration("se.scalablesolutions.akka", akkaRepo)

  // -------------------------------------------------------------------------------------------------------------------
  // Plugins
  // -------------------------------------------------------------------------------------------------------------------
  val akkaPlugin = "se.scalablesolutions.akka" % "akka-sbt-plugin" % "1.0"
  val sbtIdea = "com.github.mpeltonen" % "sbt-idea-plugin" % "0.4.0-SNAPSHOT"
}
