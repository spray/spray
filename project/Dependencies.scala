import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "spray repo" at "http://repo.spray.io/",
    "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
  )

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val akkaActor     = "com.typesafe.akka"                       %%  "akka-osgi"                   % "2.3.6"
  val akkaSlf4j     = "com.typesafe.akka"                       %%  "akka-slf4j"                  % "2.3.6"
  val akkaTestKit   = "com.typesafe.akka"                       %%  "akka-testkit"                % "2.3.6"
  val parboiled     = "org.parboiled"                           %%  "parboiled-scala"             % "1.1.6"
  val shapeless     = "com.chuusai"                             %%  "shapeless"                   % "1.2.4"
  val scalatest     = "org.scalatest"                           %%  "scalatest"                   % "2.2.2"
  val sprayJson     = "io.spray"                                %%  "spray-json"                  % "1.3.0"
  val twirlApi      = "io.spray"                                %%  "twirl-api"                   % "0.7.0"
  val playTwirlApi  = "com.typesafe.play"                       %%  "twirl-api"                   % "1.0.2"
  val clHashMap     = "com.googlecode.concurrentlinkedhashmap"  %   "concurrentlinkedhashmap-lru" % "1.4"
  val jettyWebApp   = "org.eclipse.jetty"                       %   "jetty-webapp"                % "8.1.16.v20140903"
  val servlet30     = "org.eclipse.jetty.orbit"                 %   "javax.servlet"               % "3.0.0.v201112011016" artifacts Artifact("javax.servlet", "jar", "jar")
  val logback       = "ch.qos.logback"                          %   "logback-classic"             % "1.1.2"
  val mimepull      = "org.jvnet.mimepull"                      %   "mimepull"                    % "1.9.4"
  val liftJson      = "net.liftweb"                             %%  "lift-json"                   % "2.6-RC1"
  val json4sNative  = "org.json4s"                              %%  "json4s-native"               % "3.2.10"
  val json4sJackson = "org.json4s"                              %%  "json4s-jackson"              % "3.2.10"
  val playJson      = "com.typesafe.play"                       %%  "play-json"                   % "2.3.4"

  import Keys.{libraryDependencies, scalaVersion}
  /*
   * Add scala-xml dependency when needed (for Scala 2.11 and newer) in a robust way
   * This mechanism supports cross-version publishing
   */
  val scalaXmlModule: Setting[Seq[sbt.ModuleID]] = libraryDependencies := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      // if scala 2.11+ is used, add dependency on scala-xml module
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        libraryDependencies.value :+ "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
      case _ =>
        libraryDependencies.value
    }
  }

  val addScalaReflect = libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "provided")
  def addSpecs2(config: String) = libraryDependencies <+= scalaVersion(version => "org.specs2" %% "specs2" % specs2VersionPerScala(version) % config)

  def specs2VersionPerScala(version: String): String = CrossVersion.partialVersion(version) match {
    case Some((2, 11)) => "2.3.13"
    case _ => "2.3.10"
  }

  val addShapeless2 = libraryDependencies <+= scalaVersion {
    case x if x startsWith "2.11." => "com.chuusai" %% "shapeless" % "2.0.0"
    case "2.10.4" => "com.chuusai" %% "shapeless" % "2.0.0" cross CrossVersion.full
  }
}

