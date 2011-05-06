import sbt._
import java.io.File

class Project(info: ProjectInfo) extends DefaultProject(info) {
  
  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConfigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
    lazy val JavaNetRepo = MavenRepository("java.net Repo", "http://download.java.net/maven/2")
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._
  lazy val parboiledModuleConfig = ModuleConfiguration("org.parboiled", ScalaToolsSnapshots)
  lazy val pegdownModuleConfig   = ModuleConfiguration("org.pegdown", ScalaToolsSnapshots)
  lazy val glassfishModuleConfig = ModuleConfiguration("org.glassfish", JavaNetRepo)
  
  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  object Deps {
    // compile
    val parboiledC = "org.parboiled" % "parboiled-core" % "0.12.0-SNAPSHOT" % "compile" withSources()
    val parboiledS = "org.parboiled" % "parboiled-scala" % "0.12.0-SNAPSHOT" % "compile" withSources()
    val asyncHttp  = "com.ning" % "async-http-client" % "1.6.3" % "compile" withSources()
    
    // provided
    val servlet30          = "org.glassfish" % "javax.servlet" % "3.0" % "provided" withSources()
    val jettyContinuations = "org.eclipse.jetty" % "jetty-continuation" % "7.2.0.v20101020" % "provided" withSources()
    val tomcat6            = "org.apache.tomcat" % "catalina" % "6.0.32" % "provided"
    
    // test
    val specs = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources()
    
    // only for examples
    val pegdown = "org.pegdown" % "pegdown" % "0.10.0-SNAPSHOT" % "compile" withSources()
    val JETTY_VERSION = "8.0.0.M2"
    val jettyServer = "org.eclipse.jetty" % "jetty-server" % JETTY_VERSION % "test"
    val jettyWebApp = "org.eclipse.jetty" % "jetty-webapp" % JETTY_VERSION % "test"
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // Subprojects
  // -------------------------------------------------------------------------------------------------------------------
  lazy val httpProject     = project("spray-http", "spray-http", new HttpProject(_))
  lazy val serverProject   = project("spray-server", "spray-server", new ServerProject(_))
  lazy val clientProject   = project("spray-client", "spray-client", new ClientProject(_))
  lazy val examplesProject = project("spray-examples", "spray-examples", new ExamplesProject(_))
  
  abstract class ModuleProject(info: ProjectInfo) extends DefaultProject(info) {
    // Options
    override def compileOptions = super.compileOptions ++ Seq("-deprecation", "-encoding", "utf8").map(CompileOption(_))
    override def documentOptions: Seq[ScaladocOption] = documentTitle(name + " " + version) :: Nil

    // Publishing
    //val publishTo = Resolver.file("Spray Test Repo", new File("/Users/mathias/Documents/spray/test-repo/"))
    val publishTo = "Scala Tools Snapshots" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
    //val publishTo = "Scala Tools Releases" at "http://nexus.scala-tools.org/content/repositories/releases/"
    
    Credentials(Path.userHome / ".ivy2" / ".credentials", log)
    override def managedStyle = ManagedStyle.Maven
    override def packageDocsJar = defaultJarPath("-scaladoc.jar")
    override def packageSrcJar = defaultJarPath("-sources.jar")
    lazy val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)
    lazy val docsArtifact = Artifact(artifactID, "docs", "jar", Some("scaladoc"), Nil, None)
    override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
    
    override def pomExtra = (
      <name>spray</name>
      <url>http://spray.cc/</url>
      <inceptionYear>2011</inceptionYear>
      <description>A Scala framework for building RESTful web services on top of Akka</description>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <developers>
        <developer>
          <id>sirthias</id>
          <name>Mathias Doenitz</name>
          <timezone>+1</timezone>
          <email>mathias [at] spray.cc</email>
        </developer>
      </developers>
      <scm>
        <url>http://github.com/spray/</url>
      </scm>
    )
  }
  
  class HttpProject(info: ProjectInfo) extends ModuleProject(info) {
    val parboiledC = Deps.parboiledC
    val parboiledS = Deps.parboiledS
    val specs      = Deps.specs
  }
  
  class ServerProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") withSources()
    val http               = httpProject 
    val servlet30          = Deps.servlet30
    val jettyContinuations = Deps.jettyContinuations
    val tomcat6            = Deps.tomcat6 
    val specs              = Deps.specs
  }
  
  class ClientProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") withSources()
    val http               = httpProject
    val ahc                = Deps.asyncHttp 
    val specs              = Deps.specs
  }
  
  class ExamplesProject(info: ProjectInfo) extends ParentProject(info) {
    override def disableCrossPaths = true
    val calculatorProject     = project("spray-example-calculator", "spray-example-calculator", new CalculatorProject(_))
    val markdownServerProject = project("spray-example-markdownserver", "spray-example-markdownserver", new MarkdownServerProject(_))
    val stopWatchProject      = project("spray-example-stopwatch", "spray-example-stopwatch", new StopWatchProject(_))
  }
  
  abstract class SprayExampleProject(info: ProjectInfo) extends DefaultWebProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") withSources() // always have the sources around
    val akkaHttp           = akkaModule("http")  withSources() // always have the sources around
    val http               = httpProject
    val server             = serverProject
    
    val specs       = Deps.specs
    val jettyServer = Deps.jettyServer
    val jettyWebApp = Deps.jettyWebApp
  }
  
  class CalculatorProject(info: ProjectInfo) extends SprayExampleProject(info) {
    // project specific stuff goes here
  }
  
  class MarkdownServerProject(info: ProjectInfo) extends SprayExampleProject(info) {
    val pegdown = Deps.pegdown
  }

  class StopWatchProject(info: ProjectInfo) extends SprayExampleProject(info) {
    // project specific stuff goes here
  }
}
