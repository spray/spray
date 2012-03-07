import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Glassfish Repository" at "http://download.java.net/maven/glassfish/",
    "spray repo" at "http://repo.spray.cc/",
    ScalaToolsSnapshots
  )

  object V {
    val akka      = "1.3.1"
    val jetty     = "8.1.0.v20120127"
    val sprayCan  = "0.9.3"
    val sprayJson = "1.1.0"
    val slf4j     = "1.6.4"
    val logback   = "1.0.0"
    val liftJson  = "2.4"
  }

  object Compile {
    val akkaActor     = "se.scalablesolutions.akka" %   "akka-actor"                % V.akka      % "compile"
    val parboiled     = "org.parboiled"             %   "parboiled-scala"           % "1.0.2"     % "compile"
    val mimepull      = "org.jvnet"                 %   "mimepull"                  % "1.6"       % "compile"
    val sprayCan      = "cc.spray"                  %   "spray-can"                 % V.sprayCan  % "compile"
    val sprayJson     = "cc.spray"                  %%  "spray-json"                % V.sprayJson % "compile"
    val pegdown       = "org.pegdown"               %   "pegdown"                   % "1.1.0"     % "compile"
    val clHashMap     = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.2" % "compile"
  }

  object Provided {
    val akkaActor     = "se.scalablesolutions.akka" %   "akka-actor"                % V.akka            % "provided"
    val sprayJson     = "cc.spray"                  %%  "spray-json"                % V.sprayJson       % "provided"
    val sprayCan      = "cc.spray"                  %   "spray-can"                 % V.sprayCan        % "provided"
    val servlet30     = "org.glassfish"             %   "javax.servlet"             % "3.0"             % "provided"
    val jetty7Async   = "org.eclipse.jetty"         %   "jetty-continuation"        % "7.5.1.v20110908" % "provided"
    val tomcat6Async  = "org.atmosphere"            %   "atmosphere-compat-tomcat"  % "0.7.1"           % "provided"
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % V.slf4j           % "provided"
    val liftJson      = "net.liftweb"               %%  "lift-json"                 % V.liftJson        % "provided"
    val scalate       = "org.fusesource.scalate"    %   "scalate-core"              % "1.5.3"           % "provided"
    val twirlApi      = "cc.spray"                  %%  "twirl-api"                 % "0.5.1"           % "provided"
  }

  object Test {
    val specs2        = "org.specs2"                %%  "specs2"                    % "1.7.1"   % "test"
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % V.slf4j   % "test"
    val akkaSlf4j     = "se.scalablesolutions.akka" %   "akka-slf4j"                % V.akka    % "test"
    val logback       = "ch.qos.logback"            %   "logback-classic"           % V.logback % "test"
  }

  object Runtime {
    val jettyWebApp   = "org.eclipse.jetty"         %   "jetty-webapp"              % V.jetty   % "container"
    val akkaSlf4j     = "se.scalablesolutions.akka" %   "akka-slf4j"                % V.akka
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % V.slf4j
    val logback       = "ch.qos.logback"            %   "logback-classic"           % V.logback
  }

}
