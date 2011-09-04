resolvers ++= Seq(
    "Web Plugin Repository" at "http://siasia.github.com/maven2"
)

libraryDependencies <+= sbtVersion(v => "com.github.siasia" %% "xsbt-web-plugin" % ("0.1.1-"+v))
