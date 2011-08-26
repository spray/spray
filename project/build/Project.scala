import sbt._
import Process._

class Project(info: ProjectInfo) extends DefaultProject(info) with AkkaBaseProject with posterous.Publish {

  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConfigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
    // e.g. val GlassFishRepo   = MavenRepository("GlassFishRepo Repo", "http://download.java.net/maven/glassfish/")
  }

  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._
  val sprayModuleConfig = ModuleConfiguration("cc.spray", ScalaToolsSnapshots)

  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  object Deps {
    // compile
    val slf4j = "org.slf4j" % "slf4j-api" % "1.6.1" % "compile" withSources()

    // testing with specs2, spray-client and some logging
    val specs2 = "org.specs2" %% "specs2" % "1.5" % "test" withSources()
    val dispatch_core = "net.databinder" %% "dispatch-core" % "0.8.5" withSources()
    val dispatch_http = "net.databinder" %% "dispatch-http" % "0.8.5" withSources()
    val dispatch_nio = "net.databinder" %% "dispatch-nio" % "0.8.5" withSources()
    val logback = "ch.qos.logback" % "logback-classic" % "0.9.29"
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
    <name>spray-can</name>
    <url>http://spray.cc/</url>
    <inceptionYear>2011</inceptionYear>
    <description>...</description>
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
      <url>http://github.com/spray/spray-can/</url>
    </scm>

  // -------------------------------------------------------------------------------------------------------------------
  // Subprojects
  // -------------------------------------------------------------------------------------------------------------------
  lazy val serverProject  = project("server", "server", new ServerProject(_))
  lazy val exampleProject = project("example", "example", new ExampleProject(_))

  abstract class ModuleProject(info: ProjectInfo) extends DefaultProject(info) {
    // Options
    override def compileOptions = super.compileOptions ++ scalaCompileSettings.map(CompileOption)
    override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)
  }

  class ServerProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") % "provided" withSources()
    val akkaSlf4j          = akkaModule("slf4j")
    val slf4j              = Deps.slf4j

    // testing
    val specs2        = Deps.specs2
    val dispatch_core = Deps.dispatch_core
    val dispatch_http = Deps.dispatch_http
    val dispatch_nio  = Deps.dispatch_nio
    val logback       = Deps.logback % "test"

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

  class ExampleProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    val server    = serverProject
    val akkaSlf4j = akkaModule("slf4j")
    val logback   = Deps.logback % "runtime"

    // disable publishing
    lazy override val publishLocal = task { None }
    lazy override val publish = task { None }

    override def deliverProjectDependencies = Nil
    override def disableCrossPaths = true
  }

}
