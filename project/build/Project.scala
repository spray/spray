import sbt._
import Process._

class Project(info: ProjectInfo) extends DefaultProject(info) with posterous.Publish {
  
  // -------------------------------------------------------------------------------------------------------------------
  // All repositories *must* go here! See ModuleConfigurations below.
  // -------------------------------------------------------------------------------------------------------------------
  object Repositories {
    // e.g. val AkkaRepo = MavenRepository("Akka Repository", "http://akka.io/repository")
  }
  
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  import Repositories._

  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  val parboiledC = "org.parboiled" % "parboiled-core" % "1.0.1" % "compile" withSources()
  val parboiledS = "org.parboiled" % "parboiled-scala" % "1.0.1" % "compile" withSources()
  
  val specs = "org.scala-tools.testing" %% "specs" % "1.6.8" % "test" withSources()
  
  // -------------------------------------------------------------------------------------------------------------------
  // Options
  // -------------------------------------------------------------------------------------------------------------------
  override def compileOptions = super.compileOptions ++ Seq("-deprecation", "-encoding", "utf8").map(CompileOption(_))
  override def documentOptions: Seq[ScaladocOption] = documentTitle(name + " " + version) :: Nil
  
  // -------------------------------------------------------------------------------------------------------------------
  // Publishing
  // -------------------------------------------------------------------------------------------------------------------
  //val publishTo = "Scala Tools Snapshots" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
  val publishTo = "Scala Tools Releases" at "http://nexus.scala-tools.org/content/repositories/releases/"
  
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
  override def managedStyle = ManagedStyle.Maven
  override def packageDocsJar = defaultJarPath("-scaladoc.jar")
  override def packageSrcJar = defaultJarPath("-sources.jar")
  val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)
  val docsArtifact = Artifact(artifactID, "docs", "jar", Some("scaladoc"), Nil, None)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
  
  override def pomExtra = (
    <name>spray JSON</name>
    <url>http://spray.cc/</url>
    <inceptionYear>2011</inceptionYear>
    <description>A Scala library for easy and idiomatic JSON (de)serialization</description>
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
      <url>http://github.com/spray/spray-json/</url>
    </scm>
  )
}
