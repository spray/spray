import sbt._
import Keys._

object BuildSettings {

  lazy val basicSettings = Defaults.defaultSettings ++ Seq(
    organization  := "cc.spray.can",
    version       := "0.9.1-SNAPSHOT",
    scalaVersion  := "2.9.1",
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    resolvers     ++= Dependencies.resolutionRepos
  )

  lazy val moduleSettings = basicSettings ++ Seq(
    // scaladoc
    scaladocOptions <<= (name, version).map { (n, v) => Seq("-doc-title", n + " " + v) },

    // publishing
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    crossPaths := false,
    publishMavenStyle := true,
    publishTo <<= version { version =>
      Some {
        "snapshots" at {
          "http://nexus.scala-tools.org/content/repositories/" + {
            if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else"releases/"
          }
        }
      }
    },
    pomExtra := (
      <name>spray-can</name>
      <url>http://spray.cc/</url>
      <inceptionYear>2011</inceptionYear>
      <description>a low-overhead, high-performance, fully asynchronous HTTP 1.1 server and client library
        implemented entirely in Scala on top of Akka</description>
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
        <url>http://github.com/spray/spray-can/</url>
      </scm>
    )
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := ()
  )

  lazy val exampleSettings = basicSettings ++ noPublishing
}