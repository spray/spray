import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "typesafe repo"   at "http://repo.typesafe.com/typesafe/releases/",
    "glassfish repo"  at "http://download.java.net/maven/glassfish/",
    "spray repo"      at "http://repo.spray.cc/"
  )

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val akkaActor     = "com.typesafe.akka"                       %   "akka-actor"                  % "2.0.1"
  val akkaSlf4j     = "com.typesafe.akka"                       %   "akka-slf4j"                  % "2.0.1"
  val akkaTestKit   = "com.typesafe.akka"                       %   "akka-testkit"                % "2.0.1"
  val clHashMap     = "com.googlecode.concurrentlinkedhashmap"  %   "concurrentlinkedhashmap-lru" % "1.2"
  val jetty7Async   = "org.eclipse.jetty"                       %   "jetty-continuation"          % "7.5.1.v20110908"
  val jettyWebApp   = "org.eclipse.jetty"                       %   "jetty-webapp"                % "8.1.0.v20120127"
  val liftJson      = "net.liftweb"                             %%  "lift-json"                   % "2.4"
  val logback       = "ch.qos.logback"                          %   "logback-classic"             % "1.0.0"
  val mimepull      = "org.jvnet"                               %   "mimepull"                    % "1.6"
  val parboiled     = "org.parboiled"                           %   "parboiled-scala"             % "1.0.2"
  val pegdown       = "org.pegdown"                             %   "pegdown"                     % "1.1.0"
  val scalate       = "org.fusesource.scalate"                  %   "scalate-core"                % "1.5.3"
  val servlet30     = "org.glassfish"                           %   "javax.servlet"               % "3.0"
  val slf4j         = "org.slf4j"                               %   "slf4j-api"                   % "1.6.4"
  val specs2        = "org.specs2"                              %%  "specs2"                      % "1.7.1"
  val sprayJson     = "cc.spray"                                %%  "spray-json"                  % "1.1.0"
  val tomcat6Async  = "org.atmosphere"                          %   "atmosphere-compat-tomcat"    % "0.7.1"
  val twirlApi      = "cc.spray"                                %%  "twirl-api"                   % "0.5.1"

}
