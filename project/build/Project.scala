import sbt._
import Process._

class Project(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {
  
  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
  }

  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._  
  val parboiledModuleConfig = ModuleConfiguration("org.parboiled", ScalaToolsSnapshots)
  
  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  override val akkaActor  = akkaModule("actor") withSources()
  val akkaHttp            = akkaModule("http") withSources()
  val parboiledC          = "org.parboiled" % "parboiled-core" % "0.11.0-SNAPSHOT" % "compile" withSources()
  val parboiledS          = "org.parboiled" % "parboiled-scala" % "0.11.0-SNAPSHOT" % "compile" withSources()
  
  val specs   = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources()
  val mockito = "org.mockito" % "mockito-all" % "1.8.5" % "test" withSources()
}
