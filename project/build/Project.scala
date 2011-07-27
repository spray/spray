import sbt._

class Project(info: ProjectInfo) extends DefaultProject(info) with AkkaBaseProject with posterous.Publish {
  
  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConfigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
    val GlassFishRepo = MavenRepository("GlassFishRepo Repo", "http://download.java.net/maven/glassfish/")
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._
  val glassfishModuleConfig = ModuleConfiguration("org.glassfish", GlassFishRepo)
  val sprayModuleConfig     = ModuleConfiguration("cc.spray", ScalaToolsSnapshots)

  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  object Deps {
    // compile
    val parboiledC = "org.parboiled" % "parboiled-core" % "1.0.1" % "compile" withSources()
    val parboiledS = "org.parboiled" % "parboiled-scala" % "1.0.1" % "compile" withSources()
    val asyncHttp  = "com.ning" % "async-http-client" % "1.6.4" % "compile" withSources()
    
    // provided
    val sprayJson          = "cc.spray.json" %% "spray-json" % "1.0.0" % "compile" withSources()
    val servlet30          = "org.glassfish" % "javax.servlet" % "3.0" % "provided"
    val jettyContinuations = "org.eclipse.jetty" % "jetty-continuation" % "7.2.0.v20101020" % "provided" withSources()
    val tomcat6            = "org.apache.tomcat" % "catalina" % "6.0.32" % "provided"
    
    // test
    val specs2 = "org.specs2" %% "specs2" % "1.5" % "test" withSources()
    
    // only for examples
    val logback = "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime"
    val pegdown = "org.pegdown" % "pegdown" % "1.0.2" % "compile" withSources()
    val JETTY_VERSION = "8.0.0.M3"
    val jettyServer = "org.eclipse.jetty" % "jetty-server" % JETTY_VERSION % "test"
    val jettyWebApp = "org.eclipse.jetty" % "jetty-webapp" % JETTY_VERSION % "test"
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // Compile settings
  // -------------------------------------------------------------------------------------------------------------------

  val scalaCompileSettings =
    Seq("-deprecation",
        //"-unchecked",
        //"-Xmigration",
        //"-Xcheckinit",
        //"-optimise",
        "-encoding", "utf8")

  // -------------------------------------------------------------------------------------------------------------------
  // Miscellaneous
  // -------------------------------------------------------------------------------------------------------------------
  lazy override val `package` = task { None }    // disable packaging
  lazy override val publishLocal = task { None } // and publishing
  lazy override val publish = task { None }      // the root project

  def specs2Framework = new TestFramework("org.specs2.runner.SpecsFramework")

  override def parallelExecution = true
  
  val pomExtras =
    <url>http://spray.cc/</url>
    <inceptionYear>2011</inceptionYear>
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

  // -------------------------------------------------------------------------------------------------------------------
  // Subprojects
  // -------------------------------------------------------------------------------------------------------------------
  lazy val httpProject     = project("spray-http", "spray-http", new HttpProject(_))
  lazy val serverProject   = project("spray-server", "spray-server", new ServerProject(_))
  lazy val clientProject   = project("spray-client", "spray-client", new ClientProject(_))
  lazy val examplesProject = project("spray-examples", "spray-examples", new ExamplesProject(_))
  
  abstract class ModuleProject(info: ProjectInfo) extends DefaultProject(info) {
    // Options
    override def compileOptions = super.compileOptions ++ scalaCompileSettings.map(CompileOption)
    override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)
    override def documentOptions: Seq[ScaladocOption] = documentTitle(name + " " + version) :: Nil
    override def pomExtra = pomExtras

    // Publishing
    val publishTo = "Scala Tools Snapshots" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
    //val publishTo = "Scala Tools Releases" at "http://nexus.scala-tools.org/content/repositories/releases/"
    
    Credentials(Path.userHome / ".ivy2" / ".credentials", log)
    override def managedStyle = ManagedStyle.Maven
    override def packageDocsJar = defaultJarPath("-scaladoc.jar")
    override def packageSrcJar = defaultJarPath("-sources.jar")
    lazy val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)
    lazy val docsArtifact = Artifact(artifactID, "docs", "jar", Some("scaladoc"), Nil, None)
    override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
  }
  
  class HttpProject(info: ProjectInfo) extends ModuleProject(info) {
    val parboiledC = Deps.parboiledC
    val parboiledS = Deps.parboiledS
    val specs2      = Deps.specs2
  }
  
  class ServerProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") % "provided" withSources()
    val http               = httpProject 
    val sprayJson          = Deps.sprayJson
    val servlet30          = Deps.servlet30
    val jettyContinuations = Deps.jettyContinuations
    val tomcat6            = Deps.tomcat6 
    val specs2      = Deps.specs2
  }
  
  class ClientProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") % "provided" withSources()
    val http               = httpProject
    val ahc                = Deps.asyncHttp 
    val specs2      = Deps.specs2
  }
  
  class ExamplesProject(info: ProjectInfo) extends ParentProject(info) {
    val calculatorProject     = project("spray-example-calculator", "spray-example-calculator", new CalculatorProject(_))
    val markdownServerProject = project("spray-example-markdownserver", "spray-example-markdownserver", new MarkdownServerProject(_))
    val stopWatchProject      = project("spray-example-stopwatch", "spray-example-stopwatch", new StopWatchProject(_))

    // disable publishing
    lazy override val publishLocal = task { None }
    lazy override val publish = task { None }
    
    override def deliverProjectDependencies = Nil
    override def disableCrossPaths = true
  }
  
  abstract class SprayExampleProject(info: ProjectInfo) extends DefaultWebProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") withSources() // always have the sources around
    val akkaSlf4j          = akkaModule("slf4j") withSources()
    val http               = httpProject
    val server             = serverProject
    
    val specs2      = Deps.specs2
    val jettyServer = Deps.jettyServer
    val jettyWebApp = Deps.jettyWebApp
    val logback     = Deps.logback

    override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)

    // disable publishing
    lazy override val publishLocal = task { None }
    lazy override val publish = task { None }
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
