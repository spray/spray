resolvers += "spray" at "http://repo.spray.cc"

libraryDependencies ++= Seq(
    "cc.spray" %% "spray-json" % "1.1.1",
    "org.parboiled" % "parboiled-scala" % "1.0.2" % "compile",
    "org.specs2" %% "specs2" % "1.7.1" % "test"
)
