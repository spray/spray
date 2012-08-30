import sbt._
import java.net.URL

object Dependencies {

  val resolutionRepos = Seq(
    "typesafe repo"  at "http://repo.typesafe.com/typesafe/releases/",
    "glassfish repo" at "http://download.java.net/maven/glassfish/",
    "spray repo"     at "http://repo.spray.cc/",
    Resolver.url("sbt-plugin-releases",
      new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(
        Resolver.ivyStylePatterns)
  )

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val akkaActor     = "com.typesafe.akka"                       %   "akka-actor"                  % "2.0.3"
  val akkaSlf4j     = "com.typesafe.akka"                       %   "akka-slf4j"                  % "2.0.3"
  val akkaTestKit   = "com.typesafe.akka"                       %   "akka-testkit"                % "2.0.3"
  val clHashMap     = "com.googlecode.concurrentlinkedhashmap"  %   "concurrentlinkedhashmap-lru" % "1.3.1"
  val jettyWebApp   = "org.eclipse.jetty"                       %   "jetty-webapp"                % "8.1.0.v20120127"
  val liftJson      = "net.liftweb"                             %   "lift-json_2.9.1"             % "2.4"
  val logback       = "ch.qos.logback"                          %   "logback-classic"             % "1.0.0"
  val mimepull      = "org.jvnet"                               %   "mimepull"                    % "1.6"
  val parboiled     = "org.parboiled"                           %   "parboiled-scala"             % "1.0.2"
  val pegdown       = "org.pegdown"                             %   "pegdown"                     % "1.1.0"
  val scalate       = "org.fusesource.scalate"                  %   "scalate-core"                % "1.5.3"
  val servlet30     = "org.glassfish"                           %   "javax.servlet"               % "3.0"
  val shapeless     = "com.chuusai"                             %%  "shapeless"                   % "1.2.2"
  val scalatest     = "org.scalatest"                           %%  "scalatest"                   % "1.8"
  val slf4j         = "org.slf4j"                               %   "slf4j-api"                   % "1.6.4"
  val specs2        = "org.specs2"                              %%  "specs2"                      % "1.11"
  val sprayJson     = "cc.spray"                                %%  "spray-json"                  % "1.1.1"
  val twirlApi      = "cc.spray"                                %%  "twirl-api"                   % "0.5.4"

}
