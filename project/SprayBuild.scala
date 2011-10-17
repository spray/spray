import sbt._
import Keys._
import com.github.siasia.WebPlugin

// ---------------------------------------------------------------------------------------------------------------------
// All repositories *must* go here! See ModuleConfigurations below.
// ---------------------------------------------------------------------------------------------------------------------
object Resolvers {
  val AkkaRepo        = "Akka Repository" at "http://akka.io/repository/"
  val GlassfishRepo   = "Glassfish Repository" at "http://download.java.net/maven/glassfish/"
  val SprayGithubRepo = "Spray Github Repository" at "http://spray.github.com/spray/maven/"
  val resolvers       = Seq(AkkaRepo, GlassfishRepo, SprayGithubRepo, ScalaToolsSnapshots)

  val credentials     = Credentials(Path.userHome / ".ivy2" / ".credentials")
}

// ---------------------------------------------------------------------------------------------------------------------
// Common build settings for all modules
// ---------------------------------------------------------------------------------------------------------------------
object BuildSettings {
 import Resolvers._
 
  // -------------------------------------------------------------------------------------------------------------------
  // ModuleConfigurations
  // Every dependency that cannot be resolved from the built-in repositories (Maven Central and Scala Tools Releases)
  // must be resolved from a ModuleConfiguration. This will result in a significant acceleration of the update action.
  // Therefore, if repositories are defined, this must happen as def, not as val.
  // -------------------------------------------------------------------------------------------------------------------
  val akkaModuleConfig      = ModuleConfiguration("se.scalablesolutions.akka", AkkaRepo)
  val glassfishModuleConfig = ModuleConfiguration("org.glassfish", GlassfishRepo)
  val sprayModuleConfig     = ModuleConfiguration("cc.spray", ScalaToolsSnapshots)
  val sprayJsonModuleConfig = ModuleConfiguration("cc.spray.json", ScalaToolsSnapshots)
  val sprayCanModuleConfig  = ModuleConfiguration("cc.spray.can", ScalaToolsSnapshots)

  // -------------------------------------------------------------------------------------------------------------------
  // Common settings for all modules
  // -------------------------------------------------------------------------------------------------------------------
  val buildOrganization       = "cc.spray"
  val buildVersion            = "0.8-SNAPSHOT"
  val buildScalaVersion       = "2.9.1"
  val buildCrossScalaVersions = Seq("2.9.1", "2.9.0-1")

  // Compile options
  val buildScalacOptions = Seq(
    "-deprecation",
    //"-unchecked",
    //"-Xmigration",
    //"-Xcheckinit",
    //"-optimise",
    "-encoding", "utf8"
  )

  // TODO override def documentOptions: Seq[ScaladocOption] = documentTitle(name + " " + version) :: Nil

  // Miscellaneous
  val pomExtras =
    <url>http://spray.cc/</url>
    <inceptionYear>2011</inceptionYear>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>sirthias</id>
        <name>Mathias Doenitz</name>
        <timezone>+1</timezone>
        <email>mathias [at] spray.cc</email>
      </developer>
    </developers>
    <scm>
      <url>http://github.com/spray/</url>
    </scm>

  // Create common build settings
  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization       := buildOrganization,
    version            := buildVersion,
    scalaVersion       := buildScalaVersion,
    scalacOptions      := buildScalacOptions,
    //crossScalaVersions := buildCrossScalaVersions,
    pomExtra           := pomExtras,
    publishMavenStyle  := true,
    fullResolvers     ++= resolvers,
    
    publishTo <<= (version) { version: String =>
      /*val scalaTools = "http://nexus.scala-tools.org/content/repositories/"
      if (version.trim.endsWith("SNAPSHOT")) Some("snapshots" at scalaTools + "snapshots/")
      else                                   Some("releases"  at scalaTools + "releases/")*/
      // FIXME Should be removed once testing of publishing is finished
      val repoSuffix = if (version.contains("-SNAPSHOT")) "snapshots" else "releases"
      val resolver = Resolver.file("gh-pages",
          Path.userHome / "dev" / "stefri.github.com" / "repo" / repoSuffix)
      Some(resolver)
    }
  )

  // All settings needed to completely disable publishing of an artifact
  val disablePublishing = Seq(
    publish         := (),
    publishLocal    := (),
    publishArtifact := false
  )

  // -------------------------------------------------------------------------------------------------------------------
  // Settings for spray modules
  // -------------------------------------------------------------------------------------------------------------------
 
  // Publishing

  // TODO Make sure the following options have an equivillant 0.10 setting
  //override def managedStyle = ManagedStyle.Maven
  //override def packageDocsJar = defaultJarPath("-scaladoc.jar")
  //override def packageSrcJar = defaultJarPath("-sources.jar")
  //lazy val sourceArtifact = Artifact(artifactID, "src", "jar", Some("sources"), Nil, None)
  //lazy val docsArtifact = Artifact(artifactID, "docs", "jar", Some("scaladoc"), Nil, None)
  //override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageDocs, packageSrc)

  val moduleSettings = buildSettings

  // -------------------------------------------------------------------------------------------------------------------
  // Settings for spray examples
  // -------------------------------------------------------------------------------------------------------------------
  val exampleSettings = buildSettings ++ disablePublishing ++ WebPlugin.webSettings
}

// ---------------------------------------------------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------------------------------------------------
object Dependencies {
  val AKKA_VERSION       = "1.2"
  val JETTY_VERSION      = "8.0.3.v20111011"
  val PARBOILED_VERSION  = "1.0.2"
  val SPRAY_CAN_VERSION  = "0.9.0"
  val SPRAY_JSON_VERSION = "1.0.1"
  val SLF4J_VERSION      = "1.6.1"
  val LOGBACK_VERSION    = "0.9.29"

  object Compile {
    val akkaActor     = "se.scalablesolutions.akka" %   "akka-actor"                % AKKA_VERSION       % "compile"
    val akkaSlf4j     = "se.scalablesolutions.akka" %   "akka-slf4j"                % AKKA_VERSION       % "compile"
    val parboiledC    = "org.parboiled"             %   "parboiled-core"            % PARBOILED_VERSION  % "compile"
    val parboiledS    = "org.parboiled"             %   "parboiled-scala"           % PARBOILED_VERSION  % "compile"
    val mimepull      = "org.jvnet"                 %   "mimepull"                  % "1.6"              % "compile"
    val sprayCan      = "cc.spray.can"              %%  "spray-can"                 % SPRAY_CAN_VERSION  % "compile"
    val pegdown       = "org.pegdown"               %   "pegdown"                   % "1.1.0"            % "compile"
    val sprayJson     = "cc.spray.json"             %%  "spray-json"                % SPRAY_JSON_VERSION % "compile"
  }
  object Provided {
    val sprayJson     = "cc.spray.json"             %%  "spray-json"                % SPRAY_JSON_VERSION % "provided"
    val sprayCan      = "cc.spray.can"              %%  "spray-can"                 % SPRAY_CAN_VERSION  % "provided"
    val servlet30     = "org.glassfish"             %   "javax.servlet"             % "3.0"              % "provided"
    val jettyAsync    = "org.eclipse.jetty"         %   "jetty-continuation"        % "7.5.1.v20110908"  % "provided"
    val tomcat6Async  = "org.atmosphere"            %   "atmosphere-compat-tomcat"  % "0.7.1"            % "provided"
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % SLF4J_VERSION      % "provided"
  }
  object Test {
    val specs2        = "org.specs2"                %%  "specs2"                    % "1.6.1"            % "test"
    val jettyServer   = "org.eclipse.jetty"         %   "jetty-server"              % JETTY_VERSION      % "test"
    val jettyWebApp   = "org.eclipse.jetty"         %   "jetty-webapp"              % JETTY_VERSION      % "test"
    val jettyPlugin   = "org.eclipse.jetty"         %   "jetty-webapp"              % JETTY_VERSION      % "jetty" // web-plugin
    val slf4j         = "org.slf4j"                 %   "slf4j-api"                 % SLF4J_VERSION      % "test"
    val logback       = "ch.qos.logback"            %   "logback-classic"           % LOGBACK_VERSION    % "test"
  }
  object Runtime {
    val logback       = "ch.qos.logback"            %   "logback-classic"           % LOGBACK_VERSION    % "runtime"
  }
}

object SprayBuild extends Build {
  import BuildSettings._
  import Dependencies._

  // -------------------------------------------------------------------------------------------------------------------
  // Root-Project
  // -------------------------------------------------------------------------------------------------------------------
  
  lazy val spray = Project(
    "spray",
    file("."),
    settings = buildSettings ++ disablePublishing
  ) aggregate (base, server, client, examples)


  // -------------------------------------------------------------------------------------------------------------------
  // Spray-Sub-Projects
  // -------------------------------------------------------------------------------------------------------------------

  // Module "spray-base"
  lazy val base = Project(
    "spray-base",
    file("spray-base"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.parboiledC,
        Compile.parboiledS,
        Compile.mimepull,
        Provided.sprayJson,
        Provided.sprayCan,
        Provided.slf4j,
        Test.specs2
      )
    )
  )

  // Module "spray-server"
  lazy val server = Project(
    "spray-server",
    file("spray-server"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Provided.servlet30,
        Provided.jettyAsync,
        Provided.tomcat6Async,
        Provided.sprayCan,
        Test.specs2
      )
    )
  ) dependsOn (base)

  // Module "spray-client"
  lazy val client = Project(
    "spray-client",
    file("spray-client"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.sprayCan,
        Test.specs2,
        Test.slf4j,
        Test.logback
      )
    )
  ) dependsOn (base)


  // -------------------------------------------------------------------------------------------------------------------
  // Spray-Example-Projects
  // -------------------------------------------------------------------------------------------------------------------

  // TODO override def disableCrossPaths = true

  // Common dependencies for all example modules
  val exampleDeps = Seq(
    Compile.akkaActor,
    Compile.akkaSlf4j,
    Test.specs2
  )

  // Dependencies required for running jetty examples with web plugin
  val jettyForWebPlugin = Seq(
    Test.jettyServer,
    Test.jettyWebApp,
    Test.jettyPlugin
  )

  // Parent module for all examples
  lazy val examples = Project(
    "spray-examples",
    file("spray-examples"),
    settings = exampleSettings
  ) aggregate (calculator, sprayCan, simple, markdownserver, stopwatch)

  // Module for "calculator" example
  lazy val clientExample = Project(
    "spray-client-example",
    file("spray-examples/spray-client-example"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ Seq(
        Compile.sprayCan,
        Compile.sprayJson,
        Runtime.logback
      )
    )
  ) dependsOn (base, client)

  // Module for "calculator" example
  lazy val calculator = Project(
    "spray-example-calculator",
    file("spray-examples/spray-example-calculator"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ jettyForWebPlugin ++ Seq(
        Runtime.logback
      )
    )
  ) dependsOn (base, server)

  // Module for "spray-can" example
  lazy val sprayCan = Project(
    "spray-example-spray-can",
    file("spray-examples/spray-example-spray-can"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ Seq(
        Compile.sprayCan,
        Runtime.logback
      )
    )
  ) dependsOn (base, server)

  // Module for "markdownserver" example
  lazy val markdownserver = Project(
    "spray-example-markdownserver",
    file("spray-examples/spray-example-markdownserver"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ jettyForWebPlugin ++ Seq(
        Compile.pegdown
      )
    )
  ) dependsOn (base, server)

  // Module for "stopwatch" example
  lazy val stopwatch = Project(
    "spray-example-stopwatch",
    file("spray-examples/spray-example-stopwatch"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ jettyForWebPlugin ++ Seq(
        Runtime.logback
      )
    )
  ) dependsOn (base, server)

  // Module for "simple" example
  lazy val simple = Project(
    "spray-example-simple",
    file("spray-examples/spray-example-simple"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ jettyForWebPlugin ++ Seq(
        Runtime.logback
      )
    )
  ) dependsOn (base, server)
}

