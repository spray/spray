import sbt._
import Keys._

object Build extends Build {
  import BuildSettings._

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Root Project
  // -------------------------------------------------------------------------------------------------------------------

  lazy val root = Project("root",file("."))
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)
    .aggregate(sprayUtil, sprayIo, sprayCan, examples)


  // -------------------------------------------------------------------------------------------------------------------
  // Sub Projects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val sprayCan = Project("spray-can", file("spray-can"))
      .settings(moduleSettings: _*)
      .dependsOn(sprayIo)

  lazy val sprayIo = Project("spray-io", file("spray-io"))
    .settings(moduleSettings: _*)
    .dependsOn(sprayUtil)

  lazy val sprayUtil = Project("spray-util", file("spray-util"))
    .settings(moduleSettings: _*)


  // -------------------------------------------------------------------------------------------------------------------
  // Example Projects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val examples = Project("examples", file("examples"))
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)
    .aggregate(echoServerExample, httpClientExample, httpServerExample)

  lazy val echoServerExample = Project("echo-server", file("examples/echo-server"))
    .settings(exampleSettings: _*)
    .dependsOn(sprayIo)

  lazy val httpClientExample = Project("simple-http-client", file("examples/simple-http-client"))
    .settings(exampleSettings: _*)
    .dependsOn(sprayCan)

  lazy val httpServerExample = Project("simple-http-server", file("examples/simple-http-server"))
    .settings(exampleSettings: _*)
    .dependsOn(sprayCan)

}