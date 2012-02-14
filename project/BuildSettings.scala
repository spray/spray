import sbt._
import Keys._
import ls.Plugin._

object BuildSettings {
  import Dependencies._

  lazy val basicSettings = Defaults.defaultSettings ++ lsSettings ++ Seq(
    version       := "1.0.0-SNAPSHOT",
    homepage      := Some(new URL("https://github.com/spray/spray-can")),
    organization  := "cc.spray",
    organizationHomepage := Some(new URL("http://spray.cc")),
    startYear     := Some(2011),
    licenses      := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion  := "2.9.1",
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    description   := "A low-overhead, high-performance, fully asynchronous HTTP 1.1 server and client " +
                     "library implemented entirely in Scala on top of Akka",
    resolvers     ++= Dependencies.resolutionRepos,

    (LsKeys.tags in LsKeys.lsync) := Seq("http", "server", "client", "async"),
    (LsKeys.docsUrl in LsKeys.lsync) := Some(new URL("http://spray.github.com/spray/api/spray-can/")),
    (externalResolvers in LsKeys.lsync) := Seq("spray repo" at "http://repo.spray.cc")
  )

  lazy val moduleSettings = basicSettings ++ Seq(
    // write the project version to a resource file
    resourceGenerators in Compile <+= (version, resourceManaged) map { (v, dir) =>
      val file = dir / "spray-can.version"
      IO.writeLines(file, List(v))
      Seq(file)
    },

    libraryDependencies ++= Seq(
      akkaActor % "provided",
      specs2 % "test",
      akkaSlf4j % "runtime",
      slf4j % "runtime",
      logback % "runtime"
    ),

    // scaladoc
    (scalacOptions in doc) <<= (name, version).map { (n, v) => Seq("-doc-title", n + " " + v) },

    // publishing
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    crossPaths := false,
    publishMavenStyle := true,
    publishTo <<= version { version =>
      Some {
        "spray nexus" at {
          // public uri is repo.spray.cc, we use an SSH tunnel to the nexus here
          "http://localhost:42424/content/repositories/" + {
            if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else"releases/"
          }
        }
      }
    }
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := ()
  )

  lazy val exampleSettings = basicSettings ++ noPublishing ++ Seq(
    libraryDependencies ++= Seq(
      akkaActor % "compile",
      akkaSlf4j % "runtime",
      slf4j % "runtime",
      logback % "runtime"
    )
  )
}
