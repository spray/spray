import sbt._
import Keys._

object Build extends Build {
  import Dependencies._
  import BuildSettings._

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Root Project
  // -------------------------------------------------------------------------------------------------------------------

  lazy val root = Project(
    "root",
    file("."),
    settings = basicSettings ++ noPublishing
  ) aggregate (sprayCan, clientExample, serverExample)


  // -------------------------------------------------------------------------------------------------------------------
  // Sub Projects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val sprayIo = Project(
    "spray-io",
    file("spray-io"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        akkaActor % "provided",
        specs2 % "test",
        akkaSlf4j % "runtime",
        slf4j % "runtime",
        logback % "runtime"
      )
    )
  )

  lazy val sprayCan = Project(
    "spray-can",
    file("spray-can"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        akkaActor % "provided",
        specs2 % "test",
        akkaSlf4j % "runtime",
        slf4j % "runtime",
        logback % "runtime"
      )
    )
  ) dependsOn(sprayIo)

  lazy val clientExample = Project(
    "client-example",
    file("client-example"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= Seq(
        akkaActor % "compile",
        akkaSlf4j % "runtime",
        slf4j % "runtime",
        logback % "runtime"
      )
    )
  ) dependsOn (sprayCan)

  lazy val serverExample = Project(
    "server-example",
    file("server-example"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= Seq(
        akkaActor % "compile",
        akkaSlf4j % "runtime",
        slf4j % "runtime",
        logback % "runtime"
      )
    )
  ) dependsOn (sprayCan)

}