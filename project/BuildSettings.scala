import sbt._
import Keys._
import com.github.siasia.WebPlugin
import xml.transform.{RewriteRule, RuleTransformer}

object BuildSettings {

  lazy val basicSettings = Seq[Setting[_]](
    organization  := "cc.spray",
    version       := "0.8.0",
    description   := "a suite of lightweight Scala libraries for building and consuming RESTful web services on top of Akka",
    scalaVersion  := "2.9.1",
    resolvers     ++= Dependencies.resolutionRepos
  )

  lazy val moduleSettings = basicSettings ++ Seq(
    // compiler and scaladoc settings
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    (scalacOptions in doc) <++= (name, version).map { (n, v) => Seq("-doc-title", n, "-doc-version", v) },

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
    ),

    // work-around for SBT 0.11.1 issue #257 (https://github.com/harrah/xsbt/issues/257)
    pomPostProcess := new RuleTransformer(
      new RewriteRule {
        import xml._
        override def transform(n: Node) = n match {
          case e: Elem if e.label == "classifier" => NodeSeq.Empty
          case e => e :: Nil
        }
      }
    )
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := ()
  )

  lazy val exampleSettings = basicSettings ++ noPublishing

  lazy val jettyExampleSettings = exampleSettings ++ WebPlugin.webSettings
}