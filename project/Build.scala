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
    .aggregate(examples, sprayCaching, sprayCan, sprayClient, sprayHttp, sprayHttpx,
      sprayIo, sprayRouting, sprayRoutingTests, sprayServlet, sprayTestKit, sprayUtil)
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)
    .settings(docSupportSettings: _*)


  // -------------------------------------------------------------------------------------------------------------------
  // Modules
  // -------------------------------------------------------------------------------------------------------------------

  lazy val sprayCaching = Project("spray-caching", file("spray-caching"))
    .dependsOn(sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(clHashMap) ++
      test(specs2)
    )


  lazy val sprayCan = Project("spray-can", file("spray-can"))
    .dependsOn(sprayIo, sprayHttp, sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit, specs2)
    )


  lazy val sprayClient = Project("spray-client", file("spray-client"))
    .dependsOn(sprayCan, sprayHttp, sprayHttpx)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(specs2)
    )


  lazy val sprayHttp = Project("spray-http", file("spray-http"))
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(parboiled) ++
      test(specs2)
    )


  lazy val sprayHttpx = Project("spray-httpx", file("spray-httpx"))
    .dependsOn(sprayHttp, sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(mimepull) ++
      provided(akkaActor, sprayJson, liftJson, twirlApi) ++
      test(specs2)
    )


  lazy val sprayIo = Project("spray-io", file("spray-io"))
    .dependsOn(sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit, specs2)
    )


  lazy val sprayRouting = Project("spray-routing", file("spray-routing"))
    .dependsOn(sprayCaching % "optional", sprayHttp, sprayHttpx, sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(shapeless) ++
      provided(akkaActor)
    )


  lazy val sprayRoutingTests = Project("spray-routing-tests", file("spray-routing-tests"))
    .dependsOn(sprayCaching, sprayHttp, sprayHttpx, sprayRouting, sprayTestKit, sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(shapeless) ++
      provided(akkaActor) ++
      test(specs2)
    )


  lazy val sprayServlet = Project("spray-servlet", file("spray-servlet"))
    .dependsOn(sprayHttp, sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor, servlet30)
    )


  lazy val sprayTestKit = Project("spray-testkit", file("spray-testkit"))
    .dependsOn(sprayHttp, sprayHttpx, sprayRouting, sprayUtil)
    .settings(moduleSettings: _*)
    .settings(libraryDependencies ++=
      compile(scalatest, specs2) ++
      provided(akkaActor)
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
    .aggregate(sprayCanExamples, sprayClientExamples, sprayIoExamples, sprayRoutingExamples)
    .settings(exampleSettings: _*)

  lazy val sprayCanExamples = Project("spray-can-examples", file("examples/spray-can"))
    .aggregate(simpleHttpClient, simpleHttpServer)
    .settings(exampleSettings: _*)

  lazy val simpleHttpClient = Project("simple-http-client", file("examples/spray-can/simple-http-client"))
    .dependsOn(sprayCan, sprayHttp)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback, slf4j)
    )

  lazy val simpleHttpServer = Project("simple-http-server", file("examples/spray-can/simple-http-server"))
    .dependsOn(sprayCan, sprayHttp)
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

  lazy val sprayRoutingExamples = Project("spray-routing-examples", file("examples/spray-routing"))
    .aggregate(calculator, markdownServer, simpleOnJetty, simpleOnSprayCan, stopwatch)
    .settings(exampleSettings: _*)

  lazy val calculator = Project("calculator", file("examples/spray-routing/calculator"))
    .dependsOn(sprayHttp, sprayHttpx)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp)
    )

  lazy val markdownServer = Project("markdown-server", file("examples/spray-routing/markdown-server"))
    .dependsOn(sprayHttp, sprayHttpx)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, pegdown) ++
      test(specs2) ++
      container(jettyWebApp)
    )

  lazy val simpleOnJetty = Project("simple-on-jetty", file("examples/spray-routing/simple-on-jetty"))
    .dependsOn(sprayHttp, sprayHttpx)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp)
    )

  lazy val simpleOnSprayCan = Project("simple-on-spray-can", file("examples/spray-routing/simple-on-spray-can"))
    .dependsOn(sprayHttp, sprayHttpx)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val stopwatch = Project("stopwatch", file("examples/spray-routing/stopwatch"))
    .dependsOn(sprayHttp, sprayHttpx)
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp)
    )

  lazy val sprayServletExamples = Project("spray-servlet-examples", file("examples/spray-servlet"))
    .aggregate(simpleSprayServletServer)
    .settings(exampleSettings: _*)

  lazy val simpleSprayServletServer = Project("simple-spray-servlet-server",
                                              file("examples/spray-servlet/simple-spray-servlet-server"))
    .dependsOn(sprayHttp, sprayServlet)
    .settings(jettyExampleSettings: _*)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback, slf4j) ++
      container(jettyWebApp)
    )
}