import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "Akka Repository" at "http://akka.io/repository/",
    ScalaToolsSnapshots
  )

  object V {
    val akka      = "1.2"
    val logback   = "0.9.29"
  }

  object Compile {
    val akkaActor     = "se.scalablesolutions.akka" %   "akka-actor"                % V.akka      % "compile"
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % "1.6.1"     % "compile"
  }

  object Test {
    val specs2        = "org.specs2"                %%  "specs2"                    % "1.6.1"     % "test"
    val akkaSlf4j     = "se.scalablesolutions.akka" %   "akka-slf4j"                % V.akka      % "test"
    val logback       = "ch.qos.logback"            %   "logback-classic"           % V.logback   % "test"
  }

  object Runtime {
    val akkaSlf4j     = "se.scalablesolutions.akka" %   "akka-slf4j"                % V.akka      % "runtime"
    val logback       = "ch.qos.logback"            %   "logback-classic"           % V.logback   % "runtime"
  }

}