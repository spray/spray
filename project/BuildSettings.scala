import sbt._
import Keys._


object BuildSettings {
  val VERSION = "1.0-SNAPSHOT"

  lazy val basicSettings = seq(
    version               := NightlyBuildSupport.buildVersion(VERSION),
    homepage              := Some(new URL("http://spray.io")),
    organization          := "io.spray",
    organizationHomepage  := Some(new URL("http://spray.io")),
    description           := "A suite of lightweight Scala libraries for building and consuming RESTful " +
                             "web services on top of Akka",
    startYear             := Some(2011),
    licenses              := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion          := "2.9.2",
    resolvers             ++= Dependencies.resolutionRepos,
    scalacOptions         := Seq("-Ydependent-method-types", "-unchecked", "-deprecation", "-encoding", "utf8")
  )

  lazy val sprayModuleSettings = basicSettings ++ NightlyBuildSupport.settings ++ seq(
    // scaladoc settings
    (scalacOptions in doc) <++= (name, version).map { (n, v) => Seq("-doc-title", n, "-doc-version", v) },

    // publishing
    crossPaths := false,
    publishMavenStyle := true,
    publishTo <<= version { version =>
      Some {
        "spray nexus" at {
          // public uri is repo.spray.io, we use an SSH tunnel to the nexus here
          "http://localhost:42424/content/repositories/" + {
            if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else "releases/"
          }
        }
      }
    }
  )

  lazy val noPublishing = seq(
    publish := (),
    publishLocal := ()
  )

  lazy val generateSprayVersionConf = TaskKey[Seq[File]]("generate-spray-version-conf",
    "Create a reference.conf file in the managed resources folder that contains a spray.version = ... setting")

  lazy val sprayVersionConfGeneration = seq(
    resourceGenerators in Compile <+= generateSprayVersionConf,
    generateSprayVersionConf <<= (resourceManaged in Compile, version) map { (dir, v) =>
      val file = dir / "reference.conf"
      IO.write(file, """spray.version = "%s""""+"\n" format v)
      Seq(file)
    }
  )

  lazy val exampleSettings = basicSettings ++ noPublishing

  import com.github.siasia.WebPlugin._
  lazy val jettyExampleSettings = exampleSettings ++ webSettings // ++ disableJettyLogSettings

  import com.github.siasia.PluginKeys._
  lazy val disableJettyLogSettings = inConfig(container.Configuration) {
    seq(
      start <<= (state, port, apps, customConfiguration, configurationFiles, configurationXml) map {
        (state, port, apps, cc, cf, cx) =>
          state.get(container.attribute).get.start(port, None, Utils.NopLogger, apps, cc, cf, cx)
      }
    )
  }

}