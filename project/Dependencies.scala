import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  )

  object V {
    val akka      = "2.0-RC2"
    val logback   = "1.0.0"
  }

  val akkaActor   = "com.typesafe.akka" %   "akka-actor"      % V.akka
  val akkaSlf4j   = "com.typesafe.akka" %   "akka-slf4j"      % V.akka
  val specs2      = "org.specs2"        %%  "specs2"          % "1.7.1"
  val slf4j       = "org.slf4j"         %   "slf4j-api"       % "1.6.4"
  val logback     = "ch.qos.logback"    %   "logback-classic" % V.logback
}