resolvers += "less is" at "http://repo.lessis.me"

//addSbtPlugin("me.lessis" % "ls-sbt" % "0.1.1", "0.11.2)
libraryDependencies += Defaults.sbtPluginExtra("me.lessis" % "ls-sbt" % "0.1.1", "0.11.2", "2.9.1")

addSbtPlugin("com.jsuereth" % "sbt-ghpages-plugin" % "0.4.0")

resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.5-SNAPSHOT")
