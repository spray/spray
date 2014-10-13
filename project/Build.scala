import sbt._
import Keys._

object Build extends Build {
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
    .aggregate(docs, examples, sprayCaching, sprayCan, sprayCanTests, sprayClient, sprayHttp, sprayHttpx,
      sprayIO, sprayIOTests, sprayRouting, sprayRoutingShapeless2, sprayRoutingTests, sprayRoutingShapeless2Tests, sprayServlet, sprayTestKit, sprayUtil)
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)


  // -------------------------------------------------------------------------------------------------------------------
  // Modules
  // -------------------------------------------------------------------------------------------------------------------

  lazy val sprayCaching = Project("spray-caching", file("spray-caching"))
    .dependsOn(sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(osgiSettings(exports = Seq("spray.caching")): _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(clHashMap),
      addSpecs2("test")
    )


  lazy val sprayCan = Project("spray-can", file("spray-can"))
    .dependsOn(sprayIO, sprayHttp, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(osgiSettings(exports = Seq("spray.can")): _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit),
      addSpecs2("test")
    )


  lazy val sprayCanTests = Project("spray-can-tests", file("spray-can-tests"))
    .dependsOn(sprayCan, sprayHttp, sprayHttpx, sprayIO, sprayTestKit, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(noPublishing: _*)
    .settings(libraryDependencies ++= test(akkaActor), addSpecs2("test"))


  lazy val sprayClient = Project("spray-client", file("spray-client"))
    .dependsOn(sprayCan, sprayHttp, sprayHttpx, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(osgiSettings(exports = Seq("spray.client")): _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit),
      addSpecs2("test")
    )


  lazy val sprayHttp = Project("spray-http", file("spray-http"))
    .dependsOn(sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(osgiSettings(exports = Seq("spray.http")): _*)
    .settings(libraryDependencies ++=
      compile(parboiled) ++
      provided(akkaActor),
      addSpecs2("test")
    )


  lazy val sprayHttpx = Project("spray-httpx", file("spray-httpx"))
    .dependsOn(sprayHttp, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(osgiSettings(exports = Seq("spray.httpx"), imports = Seq(
      "spray.json.*;resolution := optional",
      "net.liftweb.*;resolution := optional",
      "org.json4s.*;resolution := optional",
      "twirl.*;resolution := optional",
      "play.*;resolution := optional"
    )): _*)
    .settings(scalaXmlModule)
    .settings(libraryDependencies ++=
      compile(mimepull) ++
      provided(akkaActor, sprayJson, json4sNative, json4sJackson, twirlApi, playTwirlApi, playJson, liftJson),
      addSpecs2("test")
    )


  lazy val sprayIO = Project("spray-io", file("spray-io"))
    .dependsOn(sprayUtil, sprayHttp)
    .settings(sprayModuleSettings: _*)
    .settings(osgiSettings(exports = Seq("spray.io")): _*)
    .settings(libraryDependencies ++= provided(akkaActor), addScalaReflect)


  lazy val sprayIOTests = Project("spray-io-tests", file("spray-io-tests"))
    .dependsOn(sprayIO, sprayTestKit, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(noPublishing: _*)
    .settings(libraryDependencies ++= test(akkaActor, scalatest), addSpecs2("test"))

  def sprayRoutingProject(name: String, base: File) =
    Project(name, base)
      .dependsOn(
        sprayCaching % "provided", // for the CachingDirectives trait
        sprayCan % "provided",  // for the SimpleRoutingApp trait
        sprayHttp, sprayHttpx, sprayUtil)
      .settings(sprayModuleSettings: _*)
      .settings(spray.boilerplate.BoilerplatePlugin.Boilerplate.settings: _*)
      .settings(osgiSettings(exports = Seq("spray.routing"), imports = Seq(
        "spray.caching.*;resolution:=optional",
        "spray.can.*;resolution:=optional",
        "spray.io.*;resolution:=optional"
      )): _*)
      .settings(libraryDependencies ++= provided(akkaActor))

  lazy val sprayRouting =
    sprayRoutingProject("spray-routing", file("spray-routing"))
      .settings(libraryDependencies ++= compile(shapeless))

  val sourceWithShapeless2Changes = Set("Prepender.scala", "ShapelessSupport.scala").map(_.toLowerCase)
  lazy val sprayRoutingShapeless2 =
    sprayRoutingProject("spray-routing-shapeless2", file("spray-routing-shapeless2"))
      .settings(
        addShapeless2,
        managedSourceDirectories in Compile <<= managedSourceDirectories in Compile in sprayRouting,
        managedSources in Compile <<= managedSources in Compile in sprayRouting,
        mappings in packageSrc in Compile <<= mappings in packageSrc in Compile in sprayRouting,
        unmanagedResourceDirectories in Compile <++= (unmanagedResourceDirectories in Compile in sprayRouting),
        unmanagedSourceDirectories in Compile <++= (unmanagedSourceDirectories in Compile in sprayRouting),
        unmanagedSources in Compile ~= {
          _.filter { f =>
            val isExcluded = sourceWithShapeless2Changes(f.getName.toLowerCase)
            !(isExcluded && f.getAbsolutePath.contains("spray-routing/"))
          }
        }
      )

  def sprayRoutingTestProject(name: String, base: File) =
    Project(name, base)
      .dependsOn(sprayCaching, sprayHttp, sprayHttpx, sprayTestKit, sprayUtil)
      .settings(sprayModuleSettings: _*)
      .settings(noPublishing: _*)
      .settings(libraryDependencies ++= test(akkaActor, sprayJson), addSpecs2("test"))

  lazy val sprayRoutingTests =
    sprayRoutingTestProject("spray-routing-tests", file("spray-routing-tests"))
      .dependsOn(sprayRouting)

  lazy val sprayRoutingShapeless2Tests =
    sprayRoutingTestProject("spray-routing-shapeless2-tests", file("spray-routing-shapeless2-tests"))
      .dependsOn(sprayRoutingShapeless2)
      .settings(
        unmanagedResourceDirectories in Test <++= (unmanagedResourceDirectories in Test in sprayRoutingTests),
        unmanagedSourceDirectories in Test <<= (unmanagedSourceDirectories in Test in sprayRoutingTests)
      )

  lazy val sprayServlet = Project("spray-servlet", file("spray-servlet"))
    .dependsOn(sprayHttp, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(osgiSettings(exports = Seq("spray.servlet"), imports = Seq("javax.servlet.*;version=\"[2.6,4.0)\"")): _*)
    .settings(libraryDependencies ++=
      provided(akkaActor, servlet30),
      addSpecs2("test")
    )


  lazy val sprayTestKit = Project("spray-testkit", file("spray-testkit"))
    .dependsOn(
      sprayHttp % "provided",
      sprayHttpx % "provided",
      sprayIO % "provided",
      sprayRouting % "provided",
      sprayUtil
    )
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++= akkaTestKit +: provided(akkaActor, scalatest), addSpecs2("provided"))


  lazy val sprayUtil = Project("spray-util", file("spray-util"))
    .settings(sprayModuleSettings: _*)
    .settings(sprayVersionConfGeneration: _*)
    .settings(osgiSettings(exports = Seq("spray.util", "akka.spray")): _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit),
      addScalaReflect,
      addSpecs2("test")
    )


  // -------------------------------------------------------------------------------------------------------------------
  // Site Project
  // -------------------------------------------------------------------------------------------------------------------

  lazy val docs = Project("docs", file("docs"))
    .dependsOn(sprayCaching, sprayCan, sprayClient, sprayHttp, sprayHttpx, sprayIO, sprayRouting,
               sprayServlet, sprayTestKit, sprayUtil)
    .settings(SphinxSupport.settings: _*)
    .settings(docsSettings: _*)
    .settings(libraryDependencies ++= test(akkaActor, sprayJson), addSpecs2("test")) // , json4sNative))


  // -------------------------------------------------------------------------------------------------------------------
  // Example Projects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val examples = Project("examples", file("examples"))
    .aggregate(sprayCanExamples, sprayClientExamples, sprayIOExamples, sprayRoutingExamples, sprayServletExamples)
    .settings(exampleSettings: _*)

  lazy val sprayCanExamples = Project("spray-can-examples", file("examples/spray-can"))
    .aggregate(serverBenchmark, simpleHttpClient, simpleHttpServer)
    .settings(exampleSettings: _*)

  lazy val serverBenchmark = Project("server-benchmark", file("examples/spray-can/server-benchmark"))
    .dependsOn(sprayCan, sprayHttp)
    .settings(benchmarkSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, sprayJson) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val simpleHttpClient = Project("simple-http-client", file("examples/spray-can/simple-http-client"))
    .dependsOn(sprayCan, sprayHttp)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val simpleHttpServer = Project("simple-http-server", file("examples/spray-can/simple-http-server"))
    .dependsOn(sprayCan, sprayHttp)
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, mimepull) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val sprayClientExamples = Project("spray-client-examples", file("examples/spray-client"))
    .aggregate(simpleSprayClient)
    .settings(exampleSettings: _*)

  lazy val simpleSprayClient = Project("simple-spray-client", file("examples/spray-client/simple-spray-client"))
    .dependsOn(sprayClient)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, sprayJson) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val sprayIOExamples = Project("spray-io-examples", file("examples/spray-io"))
    .aggregate(echoServerExample)
    .settings(exampleSettings: _*)

  lazy val echoServerExample = Project("echo-server", file("examples/spray-io/echo-server"))
    .dependsOn(sprayIO)
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val sprayRoutingExamples = Project("spray-routing-examples", file("examples/spray-routing"))
    .aggregate(onJetty, onSprayCan, simpleRoutingApp)
    .settings(exampleSettings: _*)

  lazy val onJetty = Project("on-jetty", file("examples/spray-routing/on-jetty"))
    .dependsOn(sprayCaching, sprayServlet, sprayRouting, sprayTestKit % "test")
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp, servlet30),
      addSpecs2("test")
    )

  lazy val onSprayCan = Project("on-spray-can", file("examples/spray-routing/on-spray-can"))
    .dependsOn(sprayCaching, sprayCan, sprayRouting, sprayTestKit % "test")
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback),
      addSpecs2("test")
    )

  lazy val simpleRoutingApp = Project("simple-routing-app", file("examples/spray-routing/simple-routing-app"))
    .dependsOn(sprayCan, sprayRouting)
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor))

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
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp, servlet30)
    )
}
