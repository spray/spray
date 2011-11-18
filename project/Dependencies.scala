import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "Akka Repository" at "http://akka.io/repository/",
    "Glassfish Repository" at "http://download.java.net/maven/glassfish/",
    ScalaToolsSnapshots
  )

  object V {
    val akka      = "1.2"
    val jetty     = "8.0.3.v20111011"
    val sprayCan  = "0.9.1"
    val slf4j     = "1.6.1"
    val logback   = "0.9.29"
  }

  object Compile {
    val parboiled     = "org.parboiled"             %   "parboiled-scala"           % "1.0.2"     % "compile"
    val mimepull      = "org.jvnet"                 %   "mimepull"                  % "1.6"       % "compile"
    val sprayCan      = "cc.spray.can"              %   "spray-can"                 % V.sprayCan  % "compile"
    val pegdown       = "org.pegdown"               %   "pegdown"                   % "1.1.0"     % "compile"
  }

  object Provided {
    val akkaActor     = "se.scalablesolutions.akka" %   "akka-actor"                % V.akka            % "provided"
    val sprayJson     = "cc.spray.json"             %%  "spray-json"                % "1.0.1"           % "provided"
    val sprayCan      = "cc.spray.can"              %   "spray-can"                 % V.sprayCan        % "provided"
    val servlet30     = "org.glassfish"             %   "javax.servlet"             % "3.0"             % "provided"
    val jetty7Async   = "org.eclipse.jetty"         %   "jetty-continuation"        % "7.5.1.v20110908" % "provided"
    val tomcat6Async  = "org.atmosphere"            %   "atmosphere-compat-tomcat"  % "0.7.1"           % "provided"
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % V.slf4j           % "provided"
  }

  object Test {
    val specs2        = "org.specs2"                %%  "specs2"                    % "1.6.1"   % "test"
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % V.slf4j   % "test"
    val logback       = "ch.qos.logback"            %   "logback-classic"           % V.logback % "test"
  }

  object Runtime {
    val jettyWebApp   = "org.eclipse.jetty"         %   "jetty-webapp"              % V.jetty   % "container"
    val akkaSlf4j     = "se.scalablesolutions.akka" %   "akka-slf4j"                % V.akka
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % V.slf4j
    val logback       = "ch.qos.logback"            %   "logback-classic"           % V.logback
  }

}