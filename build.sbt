name := "spray-json"

version := "1.2.0-SNAPSHOT"

organization := "cc.spray"

organizationHomepage := Some(new URL("http://spray.cc"))

description := "A Scala library for easy and idiomatic JSON (de)serialization"

homepage := Some(new URL("https://github.com/spray/spray-json"))

startYear := Some(2011)

licenses := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))

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
    "spray repo" at {
      // public uri is repo.spray.cc, we use an SSH tunnel to the nexus here
      "http://localhost:42424/content/repositories/" + {
        if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else"releases/"
      }
    }
  }
}

seq(lsSettings:_*)

(LsKeys.tags in LsKeys.lsync) := Seq("json")

(LsKeys.docsUrl in LsKeys.lsync) := Some(new URL("http://spray.github.com/spray/api/spray-json/"))

(externalResolvers in LsKeys.lsync) := Seq("spray repo" at "http://repo.spray.cc")