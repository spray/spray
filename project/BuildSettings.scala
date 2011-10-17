import sbt._
import Keys._
import com.github.siasia.WebPlugin

object BuildSettings {

  lazy val basicSettings = Defaults.defaultSettings ++ Seq(
    organization  := "cc.spray",
    version       := "0.8.0-RC1",
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
      <url>http://spray.cc/</url>
      <inceptionYear>2011</inceptionYear>
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
        <url>http://github.com/spray/</url>
      </scm>
    )
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := ()
  )

  lazy val exampleSettings = basicSettings ++ noPublishing

  lazy val jettyExampleSettings = exampleSettings ++ WebPlugin.webSettings
}