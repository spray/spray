import sbt._
import Keys._

object SprayCanBuild extends Build {
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

  lazy val sprayCan = Project(
    "spray-can",
    file("spray-can"),
    settings = moduleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.slf4j,
        Test.specs2,
        Test.akkaSlf4j,
        Test.logback
      )
    )
  )

  lazy val clientExample = Project(
    "client-example",
    file("client-example"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Runtime.akkaSlf4j,
        Runtime.logback
      )
    )
  ) dependsOn (sprayCan)

  lazy val serverExample = Project(
    "server-example",
    file("server-example"),
    settings = exampleSettings ++ Seq(
      libraryDependencies ++= Seq(
        Runtime.akkaSlf4j,
        Runtime.logback
      )
    )
  ) dependsOn (sprayCan)

}