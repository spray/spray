import sbt._
import Keys._

object SprayBuild extends Build {
  import Dependencies._
  import BuildSettings._

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Root Project
  // -------------------------------------------------------------------------------------------------------------------

  lazy val spray = Project("spray", file("."))
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)
    .aggregate(base, server, client, examples)


  // -------------------------------------------------------------------------------------------------------------------
  // Modules
  // -------------------------------------------------------------------------------------------------------------------

  lazy val base = Project("spray-base", file("spray-base"))
    .settings(moduleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.parboiled,
        Compile.mimepull,
        Provided.sprayJson,
        Provided.sprayCan,
        Provided.slf4j,
        Test.specs2
      )
    )

  lazy val server = Project("spray-server", file("spray-server"))
    .settings(moduleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Provided.servlet30,
        Provided.jetty7Async,
        Provided.tomcat6Async,
        Provided.sprayCan,
        Test.specs2
      )
    )
    .dependsOn(base)

  lazy val client = Project("spray-client", file("spray-client"))
    .settings(moduleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.sprayCan,
        Test.specs2,
        Test.slf4j,
        Test.logback
      )
    )
    .dependsOn(base)


  // -------------------------------------------------------------------------------------------------------------------
  // Example Projects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val examples = Project("spray-examples", file("spray-examples"))
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)
    .aggregate(clientExample, calculatorExample, markdownserverExample, simpleExample, sprayCanExample, stopwatchExample)

  lazy val clientExample = Project("spray-client-example", file("spray-examples/spray-client-example"))
    .settings(exampleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.sprayCan,
        Compile.sprayJson,
        Test.specs2,
        Runtime.akkaSlf4j,
        Runtime.logback
      )
    )
    .dependsOn(base, client)

  lazy val calculatorExample = Project("spray-example-calculator", file("spray-examples/spray-example-calculator"))
    .settings(jettyExampleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Test.specs2,
        Runtime.akkaSlf4j,
        Runtime.jettyWebApp,
        Runtime.logback
      )
    )
    .dependsOn(base, server)

  lazy val markdownserverExample = Project("spray-example-markdownserver", file("spray-examples/spray-example-markdownserver"))
    .settings(jettyExampleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.pegdown,
        Test.specs2,
        Runtime.jettyWebApp
      )
    )
    .dependsOn(base, server)

  lazy val simpleExample = Project("spray-example-simple", file("spray-examples/spray-example-simple"))
    .settings(jettyExampleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Test.specs2,
        Runtime.akkaSlf4j,
        Runtime.jettyWebApp,
        Runtime.logback
      )
    )
    .dependsOn(base, server)

  lazy val sprayCanExample = Project("spray-example-spray-can", file("spray-examples/spray-example-spray-can"))
    .settings(exampleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.sprayCan,
        Test.specs2,
        Runtime.akkaSlf4j,
        Runtime.logback
      )
    )
    .dependsOn(base, server)

  lazy val stopwatchExample = Project("spray-example-stopwatch", file("spray-examples/spray-example-stopwatch"))
    .settings(jettyExampleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Test.specs2,
        Runtime.akkaSlf4j,
        Runtime.jettyWebApp,
        Runtime.logback
      )
    )
    .dependsOn(base, server)

}