import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "akka repo"  at "http://repo.akka.io/releases/",
    "spray repo" at "http://repo.spray.io/"
  )

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val akkaActor     = "com.typesafe.akka"                       %   "akka-actor"                  % "2.0.5"
  val akkaSlf4j     = "com.typesafe.akka"                       %   "akka-slf4j"                  % "2.0.5"
  val akkaTestKit   = "com.typesafe.akka"                       %   "akka-testkit"                % "2.0.5"
  val parboiled     = "org.parboiled"                           %   "parboiled-scala_2.9.3"       % "1.1.5"
  val shapeless     = "com.chuusai"                             %   "shapeless_2.9.2"             % "1.2.4"
  val scalatest     = "org.scalatest"                           %   "scalatest_2.9.2"             % "1.9.1"
  val specs2        = "org.specs2"                              %   "specs2_2.9.2"                % "1.12.3"
  val sprayJson     = "io.spray"                                %   "spray-json_2.9.3"            % "1.2.5"
  val twirlApi      = "io.spray"                                %   "twirl-api_2.9.2"             % "0.6.2"
  val clHashMap     = "com.googlecode.concurrentlinkedhashmap"  %   "concurrentlinkedhashmap-lru" % "1.3.2"
  val jettyWebApp   = "org.eclipse.jetty"                       %   "jetty-webapp"                % "8.1.10.v20130312"
  val servlet30     = "org.eclipse.jetty.orbit"                 %   "javax.servlet"               % "3.0.0.v201112011016" artifacts Artifact("javax.servlet", "jar", "jar")
  val logback       = "ch.qos.logback"                          %   "logback-classic"             % "1.0.12"
  val mimepull      = "org.jvnet.mimepull"                      %   "mimepull"                    % "1.9.2"
  val liftJson      = "net.liftweb"                             %   "lift-json_2.9.2"             % "2.5-RC5"
  val json4sNative  = "org.json4s"                              %   "json4s-native_2.9.2"         % "3.2.4"
  val json4sJackson = "org.json4s"                              %   "json4s-jackson_2.9.2"        % "3.2.4"
}
