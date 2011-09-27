import sbt._
import Process._

class Project(info: ProjectInfo) extends DefaultProject(info) with AkkaBaseProject with posterous.Publish {

  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  object Deps {
    // compile
    val slf4j = "org.slf4j" % "slf4j-api" % "1.6.1" % "compile" withSources()

    // testing with specs2, spray-client and some logging
    val specs2 = "org.specs2" %% "specs2" % "1.6.1" % "test" withSources()
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
    <description>a low-overhead, high-performance, fully asynchronous HTTP 1.1 server and client library
      implemented entirely in Scala on top of Akka</description>
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
  lazy val sprayCanProject  = project("spray-can", "spray-can", new SprayCanProject(_))
  lazy val serverExampleProject = project("server-example", "server-example", new ServerExampleProject(_))
  lazy val clientExampleProject = project("client-example", "client-example", new ClientExampleProject(_))

  abstract class ModuleProject(info: ProjectInfo) extends DefaultProject(info) {
    // Options
    override def compileOptions = super.compileOptions ++ scalaCompileSettings.map(CompileOption)
    override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)
  }

  class SprayCanProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    override val akkaActor = akkaModule("actor") % "provided" withSources()
    val slf4j              = Deps.slf4j

    // make version available to application
    def createVersionTxtAction = task {
      FileUtilities.write((mainResourcesOutputPath / "version.txt").asFile, version.toString, log)
      None
    }
    override def copyResourcesAction = super.copyResourcesAction && createVersionTxtAction

    // testing
    val specs2    = Deps.specs2
    val akkaSlf4j = akkaModule("slf4j") % "test"
    val logback   = Deps.logback % "test"

    // Publishing
    val publishTo = "Scala Tools Snapshots" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
    //val publishTo = "Scala Tools Releases" at "http://nexus.scala-tools.org/content/repositories/releases/"

    Credentials(Path.userHome / ".ivy2" / ".credentials", log)
    override def managedStyle = ManagedStyle.Maven
    override def packageDocsJar = defaultJarPath("-scaladoc.jar")
    override def packageSrcJar = defaultJarPath("-sources.jar")
    lazy val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)
    lazy val docsArtifact = Artifact(artifactID, "docs", "jar", Some("scaladoc"), Nil, None)
    override def packageToPublishActions =
      Seq(copyResourcesAction) ++ super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
    override def packagePaths = super.packagePaths +++ descendents(mainResourcesOutputPath ##, "version.txt")

    override def documentOptions: Seq[ScaladocOption] = documentTitle(name + " " + version) :: Nil
    override def pomExtra = pomExtras
  }

  abstract class ExampleProject(info: ProjectInfo) extends ModuleProject(info) with AkkaProject {
    val server    = sprayCanProject
    val akkaSlf4j = akkaModule("slf4j")
    val logback   = Deps.logback % "runtime"

    // disable publishing
    lazy override val publishLocal = task { None }
    lazy override val publish = task { None }

    override def deliverProjectDependencies = Nil
    override def disableCrossPaths = true
  }

  class ServerExampleProject(info: ProjectInfo) extends ExampleProject(info)

  class ClientExampleProject(info: ProjectInfo) extends ExampleProject(info)

}
