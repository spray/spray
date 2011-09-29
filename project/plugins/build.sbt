resolvers ++= Seq(
  "SBT-Web Plugin Repository" at "http://siasia.github.com/maven2"
)

addSbtPlugin("com.github.siasia" % "xsbt-web-plugin" % "0.1.2")
