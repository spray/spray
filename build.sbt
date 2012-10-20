scalaVersion := "2.9.2"

resolvers += "spray" at "http://repo.spray.io"

libraryDependencies ++= Seq(
    "io.spray" %% "spray-json" % "1.2.2",
    "org.parboiled" %% "parboiled-scala" % "1.1.3" % "compile",
    "org.specs2" %% "specs2" % "1.12.1" % "test"
)

initialCommands in console += """
    import spray.json._
    import DefaultJsonProtocol._
    import lenses._
    import JsonLenses._
"""