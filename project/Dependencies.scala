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

  val scalaReflect  = "org.scala-lang"                          %   "scala-reflect"               % "2.10.5"
  val akkaActor     = "com.typesafe.akka"                       %%  "akka-actor"                  % "2.1.4"
  val akkaSlf4j     = "com.typesafe.akka"                       %%  "akka-slf4j"                  % "2.1.4"
  val akkaTestKit   = "com.typesafe.akka"                       %%  "akka-testkit"                % "2.1.4"
  val parboiled     = "org.parboiled"                           %%  "parboiled-scala"             % "1.1.7"
  val shapeless     = "com.chuusai"                             %%  "shapeless"                   % "1.2.4"
  val scalatest     = "org.scalatest"                           %%  "scalatest"                   % "2.2.4"
  val specs2        = "org.specs2"                              %%  "specs2"                      % "2.3.10"
  val sprayJson     = "io.spray"                                %%  "spray-json"                  % "1.3.1"
  val twirlApi      = "io.spray"                                %%  "twirl-api"                   % "0.6.2"
  val playTwirlApi  = "com.typesafe.play"                       %%  "twirl-api"                   % "1.0.4"
  val clHashMap     = "com.googlecode.concurrentlinkedhashmap"  %   "concurrentlinkedhashmap-lru" % "1.4.2"
  val jettyWebApp   = "org.eclipse.jetty"                       %   "jetty-webapp"                % "9.2.10.v20150310"
  val servlet30     = "org.eclipse.jetty.orbit"                 %   "javax.servlet"               % "3.0.0.v201112011016" artifacts Artifact("javax.servlet", "jar", "jar")
  val logback       = "ch.qos.logback"                          %   "logback-classic"             % "1.1.2"
  val mimepull      = "org.jvnet.mimepull"                      %   "mimepull"                    % "1.9.5"
  val liftJson      = "net.liftweb"                             %%  "lift-json"                   % "2.6.2"
  val json4sNative  = "org.json4s"                              %%  "json4s-native"               % "3.2.11"
  val json4sJackson = "org.json4s"                              %%  "json4s-jackson"              % "3.2.11"
  val playJson      = "com.typesafe.play"                       %%  "play-json"                   % "2.3.8"
}

