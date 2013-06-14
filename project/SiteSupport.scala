import sbt._
import Keys._
import Utils._
import sbtassembly.Plugin._
import sbtassembly.Plugin.AssemblyKeys._
import spray.revolver.RevolverPlugin.Revolver
import com.decodified.scalassh._


object SiteSupport {

  val siteHost = SettingKey[String]("site-host", "Hostname of the machine running the site")
  val deployDir = SettingKey[String]("deploy-dir", "The directory to deploy to on the site-host")
  val logFile = SettingKey[String]("log-file", "The site log file location on the site-host")
  val setupSite = TaskKey[Unit]("setup-site", "Setup the site-host to prepare for site deployment")
  val deploy = TaskKey[Unit]("deploy", "Deploy the site to the site-host")

  lazy val settings = assemblySettings ++ Seq(
    siteHost := "spray.io",
    deployDir := "/opt/spray.io",
    logFile := "/opt/spray.io/site.log",
    mainClass in assembly := Some("spray.site.Main"),
    jarName in assembly := "site.jar",
    test in assembly := {},
    javaOptions in Revolver.reStart += "-Dfile.encoding=UTF8",

    setupSite <<= (siteHost, deployDir, logFile, jarName in assembly, state) map setupSite,
    deploy <<= (siteHost, deployDir, assembly, state) map deploySite
  )

  def setupSite(siteHost: String, deployDir: String, logFile: String, jarName: String, state: State): Unit = {
    job {
      colorLog(state)("[YELLOW]Setting up deployment on host %s ..." format siteHost)
      val user = HostFileConfig()(siteHost).fold(sys.error, identity).login.user
      runCommands(siteHost, state,
        //"sudo rm -Rf %s" format deployDir,
        //"sudo mkdir -p %s" format deployDir,
        //"sudo chown %s %s".format(user, deployDir),
        createFile("%s/tf" format deployDir, CommandInput(resourceContent("tf")
          .replace("#{deployDir}", deployDir)
          .replace("#{logFile}", logFile)
          .replace("#{jarName}", jarName))),
        "chmod u+x %s/tf" format deployDir
      )
    }
  }

  def deploySite(siteHost: String, deployDir: String, artifactPath: File, state: State): Unit = {
    job {
      colorLog(state)("[YELLOW]Deploying site to %s ..." format siteHost)
      val jarName = artifactPath.getName
      runCommands(siteHost, state,
        createFile("%s/%s.new".format(deployDir, jarName), CommandInput.fromFile(artifactPath)),
        "%s/tf stop" format deployDir,
        "mv %s/%s.new %s/%s".format(deployDir, jarName, deployDir, jarName),
        "sleep 1", // wait for the shutdown to complete
        "%s/tf start" format deployDir
      )
    }
  }

}
