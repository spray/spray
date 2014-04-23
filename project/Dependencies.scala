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

  val scalaReflect  = "org.scala-lang"                          %   "scala-reflect"               % "2.11.0"
  val akkaActor     = "com.typesafe.akka"                       %%  "akka-osgi"                   % "2.3.2"
  val akkaSlf4j     = "com.typesafe.akka"                       %%  "akka-slf4j"                  % "2.3.2"
  val akkaTestKit   = "com.typesafe.akka"                       %%  "akka-testkit"                % "2.3.2"
  val parboiled     = "org.parboiled"                           %%  "parboiled-scala"             % "1.1.6"
  val shapeless     = "com.chuusai"                             %%  "shapeless"                   % "1.2.4"
  val scalatest     = "org.scalatest"                           %%  "scalatest"                   % "2.1.3"
  val specs2        = "org.specs2"                              %%  "specs2"                      % "2.3.11"
  val sprayJson     = "io.spray"                                %%  "spray-json"                  % "1.2.6"
  val twirlApi      = "io.spray"                                %%  "twirl-api"                   % "0.7.0"
  val clHashMap     = "com.googlecode.concurrentlinkedhashmap"  %   "concurrentlinkedhashmap-lru" % "1.4"
  val jettyWebApp   = "org.eclipse.jetty"                       %   "jetty-webapp"                % "8.1.13.v20130916"
  val servlet30     = "org.eclipse.jetty.orbit"                 %   "javax.servlet"               % "3.0.0.v201112011016" artifacts Artifact("javax.servlet", "jar", "jar")
  val logback       = "ch.qos.logback"                          %   "logback-classic"             % "1.1.1"
  val mimepull      = "org.jvnet.mimepull"                      %   "mimepull"                    % "1.9.4"
//  val liftJson      = "net.liftweb"                             %%  "lift-json"                   % "2.5.1"
  val json4sNative  = "org.json4s"                              %%  "json4s-native"               % "3.2.9"
  val json4sJackson = "org.json4s"                              %%  "json4s-jackson"              % "3.2.9"
//  val playJson      = "com.typesafe.play"                       %%  "play-json"                   % "2.2.2"

  import Keys.{libraryDependencies, scalaVersion}
  /*
   * Add scala-xml dependency when needed (for Scala 2.11 and newer) in a robust way
   * This mechanism supports cross-version publishing
   */
  val scalaXmlModule: Setting[Seq[sbt.ModuleID]] = libraryDependencies := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      // if scala 2.11+ is used, add dependency on scala-xml module
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        libraryDependencies.value :+ "org.scala-lang.modules" %% "scala-xml" % "1.0.1"
      case _ =>
        libraryDependencies.value
    }
  }
}

