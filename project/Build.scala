import sbt._
import Keys._

object Build extends Build with DocSupport {
  import BuildSettings._
  import Dependencies._

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Root Project
  // -------------------------------------------------------------------------------------------------------------------

  lazy val root = Project("root",file("."))
    .aggregate(examples, sprayBase, sprayCan, sprayClient, sprayHttp,
      sprayHttpPlus, sprayIo, sprayServer, sprayServlet, sprayUtil)
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)
    .settings(docSupportSettings: _*)


  // -------------------------------------------------------------------------------------------------------------------
  // Modules
  // -------------------------------------------------------------------------------------------------------------------

  lazy val sprayBase = Project("spray-base", file("spray-base"))
    .dependsOn(sprayUtil, sprayCan % "provided")
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(mimepull, parboiled) ++
      provided(akkaActor, sprayJson, liftJson, twirlApi) ++
      test(specs2)
    )


  lazy val sprayCan = Project("spray-can", file("spray-can"))
    .dependsOn(sprayIo)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit, specs2)
    )


  lazy val sprayClient = Project("spray-client", file("spray-client"))
    .dependsOn(sprayBase, sprayCan)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaSlf4j, logback, slf4j, specs2)
    )


  lazy val sprayHttp = Project("spray-http", file("spray-http"))
    .dependsOn(sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(mimepull, parboiled) ++
      test(specs2)
    )


  lazy val sprayHttpPlus = Project("spray-http-plus", file("spray-http-plus"))
    .dependsOn(sprayHttp)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaSlf4j, logback, slf4j, specs2)
    )


  lazy val sprayIo = Project("spray-io", file("spray-io"))
    .dependsOn(sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit, specs2)
    )


  lazy val sprayServer = Project("spray-server", file("spray-server"))
    .dependsOn(sprayBase, sprayCan % "provided")
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(clHashMap) ++
      provided(akkaActor, jetty7Async, scalate, servlet30, tomcat6Async) ++
      test(specs2)
    )


  lazy val sprayServlet = Project("spray-servlet", file("spray-servlet"))
    .dependsOn(sprayHttp)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor, jetty7Async, servlet30, tomcat6Async) ++
      test(specs2)
    )


  lazy val sprayUtil = Project("spray-util", file("spray-util"))
    .settings(moduleSettings: _*)
    .settings(sprayVersionConfGeneration: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit, specs2)
    )


  // -------------------------------------------------------------------------------------------------------------------
  // Example Projects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val examples = Project("examples", file("examples"))
    .aggregate(sprayCanExamples, sprayClientExamples, sprayIoExamples, sprayServerExamples)
    .settings(exampleSettings: _*)

  lazy val sprayCanExamples = Project("spray-can-examples", file("examples/spray-can"))
    .aggregate(simpleHttpClient, simpleHttpServer)
    .settings(exampleSettings: _*)

  lazy val simpleHttpClient = Project("simple-http-client", file("examples/spray-can/simple-http-client"))
    .dependsOn(sprayCan)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback, slf4j)
    )

  lazy val simpleHttpServer = Project("simple-http-server", file("examples/spray-can/simple-http-server"))
    .dependsOn(sprayCan)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback, slf4j)
    )

  lazy val sprayClientExamples = Project("spray-client-examples", file("examples/spray-client"))
    .aggregate(simpleSprayClient)
    .settings(exampleSettings: _*)

  lazy val simpleSprayClient = Project("simple-spray-client", file("examples/spray-client/simple-spray-client"))
    .dependsOn(sprayClient)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, sprayJson) ++
      runtime(akkaSlf4j, logback, slf4j)
    )

  lazy val sprayIoExamples = Project("spray-io-examples", file("examples/spray-io"))
    .aggregate(echoServerExample)
    .settings(exampleSettings: _*)

  lazy val echoServerExample = Project("echo-server", file("examples/spray-io/echo-server"))
    .dependsOn(sprayIo)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback, slf4j)
    )

  lazy val sprayServerExamples = Project("spray-server-examples", file("examples/spray-server"))
    .aggregate(calculator, markdownServer, simpleOnJetty, simpleOnSprayCan, stopwatch)
    .settings(exampleSettings: _*)

  lazy val calculator = Project("calculator", file("examples/spray-server/calculator"))
    .dependsOn(sprayBase, sprayServer)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp)
    )

  lazy val markdownServer = Project("markdown-server", file("examples/spray-server/markdown-server"))
    .dependsOn(sprayBase, sprayServer)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, pegdown) ++
      test(specs2) ++
      container(jettyWebApp)
    )

  lazy val simpleOnJetty = Project("simple-on-jetty", file("examples/spray-server/simple-on-jetty"))
    .dependsOn(sprayBase, sprayServer)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp)
    )

  lazy val simpleOnSprayCan = Project("simple-on-spray-can", file("examples/spray-server/simple-on-spray-can"))
    .dependsOn(sprayBase, sprayServer, sprayCan)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val stopwatch = Project("stopwatch", file("examples/spray-server/stopwatch"))
    .dependsOn(sprayBase, sprayServer)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp)
    )
}