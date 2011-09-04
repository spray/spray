resolvers ++= Seq(
    "Web Plugin Repository" at "http://siasia.github.com/maven2"
)

libraryDependencies <++= sbtVersion(v => Seq(
    "com.github.siasia" %% "xsbt-web-plugin" % ("0.1.1-"+v),
    "net.databinder" %% "posterous-sbt" % ("0.3.0_sbt"+v)
))
