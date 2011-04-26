import sbt._
import java.io.File

class Project(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {
  
  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConfigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
    // e.g. val akkaRepo = MavenRepository("Akka Repository", "http://akka.io/repository")
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._
  lazy val parboiledModuleConfig   = ModuleConfiguration("org.parboiled", ScalaToolsSnapshots)
  
  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  override val akkaActor  = akkaModule("actor") withSources()
  val parboiledC          = "org.parboiled" % "parboiled-core" % "0.12.0-SNAPSHOT" % "compile" withSources()
  val parboiledS          = "org.parboiled" % "parboiled-scala" % "0.12.0-SNAPSHOT" % "compile" withSources()
  
  val specs   = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources()
  
  // -------------------------------------------------------------------------------------------------------------------
  // Options
  // -------------------------------------------------------------------------------------------------------------------
  override def compileOptions = super.compileOptions ++ Seq("-deprecation", "-encoding", "utf8").map(CompileOption(_))
  override def documentOptions: Seq[ScaladocOption] = documentTitle("Spray " + version) :: Nil
  
  // -------------------------------------------------------------------------------------------------------------------
  // Publishing
  // -------------------------------------------------------------------------------------------------------------------
  //val publishTo = Resolver.file("Spray Test Repo", new File("/Users/mathias/Documents/spray/test-repo/"))
  val publishTo = "Scala Tools Snapshots" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
  //val publishTo = "Scala Tools Releases" at "http://nexus.scala-tools.org/content/repositories/releases/"
  
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
  override def managedStyle = ManagedStyle.Maven
  override def packageDocsJar = defaultJarPath("-scaladoc.jar")
  override def packageSrcJar = defaultJarPath("-sources.jar")
  val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)
  val docsArtifact = Artifact(artifactID, "docs", "jar", Some("scaladoc"), Nil, None)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)

  // -------------------------------------------------------------------------------------------------------------------
  // Extra POM metadata
  // -------------------------------------------------------------------------------------------------------------------
  override def pomExtra = (
    <name>Spray</name>
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
      <url>http://github.com/sirthias/spray/</url>
    </scm>
  )
}
