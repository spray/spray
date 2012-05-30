libraryDependencies <+= sbtVersion(v => "com.github.siasia" %% "xsbt-web-plugin" % (v + "-0.2.11.1"))

addSbtPlugin("me.lessis" % "ls-sbt" % "0.1.1")

resolvers ++= Seq(
  Resolver.url("sbt-plugin-releases", new URL(
    "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(
      Resolver.ivyStylePatterns),
  "coda" at "http://repo.codahale.com"
)