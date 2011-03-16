import sbt._
import Process._

class Project(info: ProjectInfo) extends ParentProject(info) {
  
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  lazy val parboiledModuleConfig   = ModuleConfiguration("org.parboiled", ScalaToolsSnapshots)
  
  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  object Deps {
    lazy val parboiledC = "org.parboiled" % "parboiled-core" % "0.11.0-SNAPSHOT" % "compile" withSources()
    lazy val parboiledS = "org.parboiled" % "parboiled-scala" % "0.11.0-SNAPSHOT" % "compile" withSources()
    
    lazy val specs   = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources()
    lazy val mockito = "org.mockito" % "mockito-all" % "1.8.5" % "test" withSources()
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // Subprojects
  // -------------------------------------------------------------------------------------------------------------------
  lazy val sprayProject     = project("Spray", "Spray", new SprayProject(_))
  lazy val examplesProject = project("Examples", "Examples", new ExamplesProject(_))
  
  class SprayProject(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") withSources()
    val akkaHttp = akkaModule("http") withSources()
    val parboiledC = Deps.parboiledC
    val parboiledS = Deps.parboiledS
    
    val specs = Deps.specs
    val mockito = Deps.mockito
  }
  
  class ExamplesProject(info: ProjectInfo) extends DefaultWebProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") withSources()
    val akkaHttp = akkaModule("http") withSources()
    val spray = sprayProject
    
    val jetty7Server = "org.eclipse.jetty" % "jetty-server" % "8.0.0.M2" % "test"
    val jetty7WebApp = "org.eclipse.jetty" % "jetty-webapp" % "8.0.0.M2" % "test"
  }
}
