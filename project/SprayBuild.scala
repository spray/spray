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
  val buildVersion            = "0.8-AKKA12-SNAPSHOT"
  val buildScalaVersion       = "2.9.1"
  val buildCrossScalaVersions = Seq("2.9.1", "2.9.0-1")

  // Compile options
  val buildScalacOptions = 
    Seq("-deprecation",
      //"-unchecked",
      //"-Xmigration",
      //"-Xcheckinit",
      //"-optimise",
      "-encoding", "utf8")

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
  val AKKA_VERSION      = "1.2"
  val JETTY_VERSION     = "8.0.0.RC0"
  val PARBOILED_VERSION = "1.0.1"

  // compile
  val akkaActor  = "se.scalablesolutions.akka" % "akka-actor" % AKKA_VERSION
  val parboiledC = "org.parboiled" % "parboiled-core" % PARBOILED_VERSION % "compile"
  val parboiledS = "org.parboiled" % "parboiled-scala" % PARBOILED_VERSION % "compile"
  val asyncHttp  = "com.ning" % "async-http-client" % "1.6.4" % "compile"

  // provided
  val sprayJson          = "cc.spray.json" % "spray-json_2.9.0-1" % "1.0.0" % "compile"
  val servlet30          = "org.glassfish" % "javax.servlet" % "3.0" % "provided"
  val jettyContinuations = "org.eclipse.jetty" % "jetty-continuation" % "7.2.0.v20101020" % "provided"
  val tomcat6            = "org.atmosphere" % "atmosphere-compat-tomcat" % "0.7.1" % "provided"
  val deft               = "org.apache.deft" % "deft" % "0.4.0-SNAPSHOT" % "provided"
  val sprayCan           = "cc.spray.can" %% "spray-can" % "0.5.0-SNAPSHOT" % "provided"

  // test
  val specs2 = "org.specs2" %% "specs2" % "1.6.1" % "test"

  // only for examples
  val akkaSlf4j   = "se.scalablesolutions.akka" % "akka-slf4j" % AKKA_VERSION
  val slf4j       = "org.slf4j" % "slf4j-api" % "1.6.1" % "compile"
  val logback     = "ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime"
  val pegdown     = "org.pegdown" % "pegdown" % "1.0.2" % "compile"
  val jettyServer = "org.eclipse.jetty" % "jetty-server" % JETTY_VERSION % "test"
  val jettyWebApp = "org.eclipse.jetty" % "jetty-webapp" % JETTY_VERSION % "test"

  // only for example execution with web-plugin
  val jettyPlugin = "org.eclipse.jetty" % "jetty-webapp" % JETTY_VERSION % "jetty"
}

object SprayBuild extends Build {
  import BuildSettings._

  // -------------------------------------------------------------------------------------------------------------------
  // Root-Project
  // -------------------------------------------------------------------------------------------------------------------
  
  lazy val spray = Project(
    "spray",
    file("."),
    settings = buildSettings ++ disablePublishing
  ) aggregate (http, server, client, examples)


  // -------------------------------------------------------------------------------------------------------------------
  // Spray-Sub-Projects
  // -------------------------------------------------------------------------------------------------------------------

  // Module "spray-http"
  lazy val http = Project(
    "spray-http",
    file("spray-http"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependencies.parboiledC, 
        Dependencies.parboiledS,
        Dependencies.specs2
      )
    )
  )

  // Module "spray-server"
  lazy val server = Project(
    "spray-server",
    file("spray-server"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependencies.akkaActor,
        Dependencies.sprayJson,
        Dependencies.servlet30,
        Dependencies.jettyContinuations,
        Dependencies.tomcat6,
        Dependencies.deft,
        Dependencies.specs2,
        Dependencies.sprayCan
      )
    )
  ) dependsOn (http)

  // Module "spray-client"
  lazy val client = Project(
    "spray-client",
    file("spray-client"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Dependencies.akkaActor,
        Dependencies.asyncHttp,
        Dependencies.specs2
      )
    )
  ) dependsOn (http)


  // -------------------------------------------------------------------------------------------------------------------
  // Spray-Example-Projects
  // -------------------------------------------------------------------------------------------------------------------

  // TODO override def disableCrossPaths = true

  // Common dependencies for all example modules
  val exampleDeps = Seq(
    Dependencies.akkaActor,
    Dependencies.akkaSlf4j,
    Dependencies.specs2
  )

  // Parent module for all examples
  lazy val examples = Project(
    "spray-examples",
    file("spray-examples"),
    settings = exampleSettings
  ) aggregate (calculator, sprayCan, markdownserver, stopwatch)

  // Module for "calculator" example
  lazy val calculator = Project(
    "spray-example-calculator",
    file("spray-examples/spray-example-calculator"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ Seq(
        Dependencies.jettyServer,
        Dependencies.jettyWebApp,
        Dependencies.jettyPlugin,
        Dependencies.logback
      )
    )
  ) dependsOn (http, server)

  // Module for "spray-can" example
  lazy val sprayCan = Project(
    "spray-example-spray-can",
    file("spray-examples/spray-example-spray-can"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ Seq(
        Dependencies.sprayCan,
        Dependencies.slf4j,
        Dependencies.logback
      )
    )
  ) dependsOn (http, server)

  // Module for "markdownserver" example
  lazy val markdownserver = Project(
    "spray-example-markdownserver",
    file("spray-examples/spray-example-markdownserver"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ Seq(
        Dependencies.pegdown,
        Dependencies.jettyServer,
        Dependencies.jettyWebApp,
        Dependencies.jettyPlugin,
        Dependencies.logback
      )
    )
  ) dependsOn (http, server)

  // Module for "stopwatch" example
  lazy val stopwatch = Project(
    "spray-example-stopwatch",
    file("spray-examples/spray-example-stopwatch"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= exampleDeps ++ Seq(
        Dependencies.jettyServer,
        Dependencies.jettyWebApp,
        Dependencies.jettyPlugin,
        Dependencies.logback
      )
    )
  ) dependsOn (http, server)
}

