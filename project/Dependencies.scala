import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "spray repo" at "http://repo.spray.io/"
  )

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val akkaActor     = "com.typesafe.akka"                       %%  "akka-actor"                  % "2.1.0"
  val akkaSlf4j     = "com.typesafe.akka"                       %%  "akka-slf4j"                  % "2.1.0"
  val akkaTestKit   = "com.typesafe.akka"                       %%  "akka-testkit"                % "2.1.0"
  val parboiled     = "org.parboiled"                           %%  "parboiled-scala"             % "1.1.4"
  val shapeless     = "com.chuusai"                             %%  "shapeless"                   % "1.2.3"
  val scalatest     = "org.scalatest"                           %%  "scalatest"                   % "1.9.1"
  val specs2        = "org.specs2"                              %%  "specs2"                      % "1.12.3"
  val sprayJson     = "io.spray"                                %%  "spray-json"                  % "1.2.3"
  val twirlApi      = "io.spray"                                %%  "twirl-api"                   % "0.6.1"
  val clHashMap     = "com.googlecode.concurrentlinkedhashmap"  %   "concurrentlinkedhashmap-lru" % "1.3.2"
  val jettyWebApp   = "org.eclipse.jetty"                       %   "jetty-webapp"                % "8.1.8.v20121106"
  val servlet30     = "org.eclipse.jetty.orbit"                 %   "javax.servlet"               % "3.0.0.v201112011016" artifacts Artifact("javax.servlet", "jar", "jar")
  val logback       = "ch.qos.logback"                          %   "logback-classic"             % "1.0.9"
  val mimepull      = "org.jvnet.mimepull"                      %   "mimepull"                    % "1.9.1"
  val pegdown       = "org.pegdown"                             %   "pegdown"                     % "1.2.1"
}
