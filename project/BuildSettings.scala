import sbt._
import Keys._
import com.typesafe.sbt.SbtPgp
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin._
import sbtunidoc.Plugin._
import sbtunidoc.Plugin.UnidocKeys._
import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbt.osgi.SbtOsgi
import SbtOsgi._

object BuildSettings {
  val VERSION = "1.1-SNAPSHOT"

  lazy val basicSettings = seq(
    version               := NightlyBuildSupport.buildVersion(VERSION),
    homepage              := Some(new URL("http://spray.io")),
    organization          := "io.spray",
    organizationHomepage  := Some(new URL("http://spray.io")),
    description           := "A suite of lightweight Scala libraries for building and consuming RESTful " +
                             "web services on top of Akka",
    startYear             := Some(2011),
    licenses              := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion          := "2.10.4",
    resolvers             ++= Dependencies.resolutionRepos,
    scalacOptions         := Seq(
      "-encoding", "utf8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-target:jvm-1.6",
      "-language:_",
      "-Xlog-reflective-calls"
    )
  )

  lazy val sprayModuleSettings =
    basicSettings ++ formatSettings ++
    NightlyBuildSupport.settings ++
    net.virtualvoid.sbt.graph.Plugin.graphSettings ++
    SbtPgp.settings ++
    seq(
      // scaladoc settings
      (scalacOptions in doc) <++= (name, version).map { (n, v) => Seq("-doc-title", n, "-doc-version", v) },

      // publishing
      crossPaths := false,
      publishMavenStyle := true,
      SbtPgp.useGpg := true,
      publishTo <<= version { version =>
        Some {
          if (version.contains("-") || true) { // sonatype publishing currently disabled
            "spray nexus" at {
              // public uri is repo.spray.io, we use an SSH tunnel to the nexus here
              "http://localhost:42424/content/repositories/" + {
                if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else
                if (NightlyBuildSupport.isNightly) "nightlies/" else "releases/"
              }
            }
          } else "sonatype release staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
        }
      },
      pomIncludeRepository := { _ => false },
      pomExtra :=
        <scm>
          <url>git://github.com/spray/spray.git</url>
          <connection>scm:git:git@github.com:spray/spray.git</connection>
        </scm>
        <developers>
          <developer><id>sirthias</id><name>Mathias Doenitz</name></developer>
          <developer><id>jrudolph</id><name>Johannes Rudolph</name></developer>
        </developers>
    )

  lazy val noPublishing = seq(
    publish := (),
    publishLocal := (),
    // required until these tickets are closed https://github.com/sbt/sbt-pgp/issues/42,
    // https://github.com/sbt/sbt-pgp/issues/36
    publishTo := None
  )

  lazy val generateSprayVersionConf = TaskKey[Seq[File]]("generate-spray-version-conf",
    "Create a reference.conf file in the managed resources folder that contains a spray.version = ... setting")

  lazy val sprayVersionConfGeneration = seq(
    (unmanagedResources in Compile) <<= (unmanagedResources in Compile).map(_.filter(_.getName != "reference.conf")),
    resourceGenerators in Compile <+= generateSprayVersionConf,
    generateSprayVersionConf <<= (unmanagedResourceDirectories in Compile, resourceManaged in Compile, version) map {
      (sourceDir, targetDir, version) => {
        val source = sourceDir / "reference.conf"
        val target = targetDir / "reference.conf"
        val conf = IO.read(source.get.head)
        IO.write(target, conf.replace("<VERSION>", version))
        Seq(target)
      }
    }
  )

  lazy val siteSettings = basicSettings ++ formatSettings ++ noPublishing ++ Revolver.settings ++
    SiteSupport.settings ++ seq(
      resourceGenerators in Compile <+= (target in ScalaUnidoc in unidoc in LocalRootProject){ docsLocation =>
        constant(Seq(docsLocation)).map(_.flatMap(_.***.get))
      },
      assembly <<= assembly.dependsOn(unidoc in Compile in LocalRootProject)
    )

  lazy val docsSettings = basicSettings ++ noPublishing ++ seq(
    unmanagedSourceDirectories in Test <<= baseDirectory { _ ** "code" get }
  )

  lazy val exampleSettings = basicSettings ++ noPublishing
  lazy val standaloneServerExampleSettings = exampleSettings ++ Revolver.settings

  lazy val benchmarkSettings = basicSettings ++ noPublishing ++ Revolver.settings ++ assemblySettings ++ Seq(
    mainClass in assembly := Some("spray.examples.Main"),
    jarName in assembly := "benchmark.jar",
    test in assembly := {},
    javaOptions in Revolver.reStart ++= Seq("-verbose:gc", "-XX:+PrintCompilation")
  )

  lazy val jettyExampleSettings = exampleSettings ++ com.earldouglas.xwp.XwpPlugin.jetty()

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  import scalariform.formatter.preferences._
  def formattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)

  def osgiSettings(exports: Seq[String], imports: Seq[String] = Seq.empty) =
    SbtOsgi.osgiSettings ++ Seq(
      OsgiKeys.exportPackage := exports map { pkg => pkg + ".*;version=\"${Bundle-Version}\"" },
      OsgiKeys.importPackage <<= scalaVersion { sv => Seq("""scala.*;version="$<range;[==,=+);%s>"""".format(sv)) },
      OsgiKeys.importPackage ++= imports,
      OsgiKeys.importPackage += "akka.io.*;version=\"${Bundle-Version}\"",
      OsgiKeys.importPackage += "akka.spray.*;version=\"${Bundle-Version}\"",
      OsgiKeys.importPackage += """akka.*;version="$<range;[==,=+);$<@>>"""",
      OsgiKeys.importPackage += "*",
      OsgiKeys.additionalHeaders := Map("-removeheaders" -> "Include-Resource,Private-Package")
    )

}
