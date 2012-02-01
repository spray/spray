name := "spray-json"

organization := "cc.spray"

version := "1.1.0"

description := "A Scala library for easy and idiomatic JSON (de)serialization"

scalaVersion := "2.9.1"

scalacOptions := Seq("-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "org.parboiled" % "parboiled-scala" % "1.0.2" % "compile",
  "org.specs2" %% "specs2" % "1.6.1" % "test"
)

scaladocOptions <<= (name, version).map { (n, v) => Seq("-doc-title", n + " " + v) }

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishMavenStyle := true

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
    <url>http://github.com/spray/spray-json/</url>
  </scm>
)