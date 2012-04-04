import sbt._
import Keys._
import com.github.siasia.WebPlugin._
import sbtbuildinfo.Plugin._
import ls.Plugin._

object BuildSettings {

  lazy val basicSettings = seq(
    version               := "0.9.0",
    homepage              := Some(new URL("http://spray.cc")),
    organization          := "cc.spray",
    organizationHomepage  := Some(new URL("http://spray.cc")),
    description           := "A suite of lightweight Scala libraries for building and consuming RESTful " +
                             "web services on top of Akka",
    startYear             := Some(2011),
    licenses              := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion          := "2.9.1",
    resolvers             ++= Dependencies.resolutionRepos,
    scalacOptions         := Seq("-deprecation", "-encoding", "utf8")
  )

  lazy val moduleSettings = basicSettings ++ seq(
    // scaladoc settings
    (scalacOptions in doc) <++= (name, version).map { (n, v) => Seq("-doc-title", n, "-doc-version", v) },

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
    },

    // LS
    (LsKeys.tags in LsKeys.lsync) := Seq("http", "server", "client", "async"),
    (LsKeys.docsUrl in LsKeys.lsync) := Some(new URL("http://spray.github.com/spray/api/spray-can/")),
    (externalResolvers in LsKeys.lsync) := Seq("spray repo" at "http://repo.spray.cc")
  )

  lazy val noPublishing = seq(
    publish := (),
    publishLocal := ()
  )

  lazy val buildInfoSettings = sbtbuildinfo.Plugin.buildInfoSettings ++ seq(
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[Scoped](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "cc.spray"
  )

  lazy val exampleSettings = basicSettings ++ noPublishing

  lazy val jettyExampleSettings = exampleSettings ++ webSettings
}