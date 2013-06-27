resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= Seq(
  "com.github.siasia" %% "xsbt-web-plugin" % "0.12.0-0.2.11.1",
  "com.decodified" % "scala-ssh" % "0.6.2",
  "org.bouncycastle" % "bcprov-jdk16" % "1.46",
  "com.jcraft" % "jzlib" % "1.1.1"
)

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.0")

addSbtPlugin("io.spray" % "sbt-twirl" % "0.6.1")

addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.5.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.8")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.0.1")
