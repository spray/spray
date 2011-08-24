import sbt._
import Process._

class Project(info: ProjectInfo) extends DefaultProject(info) with AkkaProject with posterous.Publish {

  // -------------------------------------------------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------------------------------------------------
  override val akkaActor = akkaModule("actor") % "provided" withSources()
  val slf4j = "org.slf4j" % "slf4j-api" % "1.6.1" % "compile" withSources()

  // -------------------------------------------------------------------------------------------------------------------
  // Testing with Specs2
  // -------------------------------------------------------------------------------------------------------------------
  val specs2 = "org.specs2" %% "specs2" % "1.5" % "test" withSources()

  def specs2Framework = new TestFramework("org.specs2.runner.SpecsFramework")
  override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)

  // -------------------------------------------------------------------------------------------------------------------
  // Options
  // -------------------------------------------------------------------------------------------------------------------
  override def compileOptions = super.compileOptions ++ Seq("-deprecation", "-encoding", "utf8").map(CompileOption(_))
  override def documentOptions: Seq[ScaladocOption] = documentTitle(name + " " + version) :: Nil
  
  // -------------------------------------------------------------------------------------------------------------------
  // Publishing
  // -------------------------------------------------------------------------------------------------------------------
  val publishTo = "Scala Tools Snapshots" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
  //val publishTo = "Scala Tools Releases" at "http://nexus.scala-tools.org/content/repositories/releases/"
  
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
  override def managedStyle = ManagedStyle.Maven
  override def packageDocsJar = defaultJarPath("-scaladoc.jar")
  override def packageSrcJar = defaultJarPath("-sources.jar")
  val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)
  val docsArtifact = Artifact(artifactID, "docs", "jar", Some("scaladoc"), Nil, None)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)
  
  override def pomExtra = (
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
  )
}
