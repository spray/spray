import sbt._
import Keys._
import scala.Console.{RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE}

object DocSupport {

  val makeDocs = InputKey[File]("make-docs", "Runs `make clean html` in the /docs directory")
  val publishDocs = InputKey[Unit]("publish-docs", "Runs `make clean html` in the /docs directory")

  val settings = seq(

    makeDocs <<= inputTask { taskArgs =>
      (baseDirectory, version, state, taskArgs) map doMakeDocs("html")
    },

    publishDocs <<= inputTask { taskArgs =>
      (baseDirectory, state, taskArgs) map { (baseDir, st, args) =>
        implicit val pubDir = Path(baseDir).asFile.getParentFile / "spray.github.com"
        args match {
          case v :: force => doPublishDocs(st, baseDir, pubDir / v, force.size == 1 && force.head == "-c")
          case _ => colorLog(st)("[YELLOW]Usage: [WHITE]publish-docs [CYAN]version [-c]")
        }
      }
    }
  )

  def doMakeDocs(what: String)(baseDir: File, version: String, st: State, args: Seq[String] = Nil): File = {
    val docsDir = baseDir / "/docs"
    def make() = {
      val log = colorLog(st)
      log("[YELLOW]: Rebuilding doc site at " + docsDir)
      val exitCode = Process {
        "make" ::
        "-C" :: docsDir.toString ::
        "clean" :: what ::
        "SPHINXOPTS = -D version=%s -D release=%s".format(version, version) :: Nil
      } ! CommandSupport.logger(st)
      if (exitCode != 0) sys.error("Error making the doc site")
      docsDir / "target" / what
    }
    args match {
      case Nil => make()
      case "-o" :: Nil =>
        val result = make()
        openBrowser("file://localhost" + result + "/index.html")
        result
      case _ =>
        colorLog(st)("[YELLOW]Usage: [WHITE]make-docs [CYAN][-o]")
        docsDir
    }
  }

  def doPublishDocs(st: State, baseDir: File, versionDir: File, create: Boolean)(implicit pubDir: File) {
    val version = versionDir.getName
    val log = colorLog(st)

    log("[YELLOW]Checking if publishing directory '" + pubDir + "' exists ...")
    if (!pubDir.exists)
      sys.error("Cannot find publishing directory " + pubDir)

    log("[YELLOW]Checking if publishing directory is a GIT repo ...")
    if (!(pubDir / ".git").exists)
      sys.error("Not a git repository " + pubDir)

    log("[YELLOW]Checking if publishing directory is currently on the 'master' branch ...")
    if (git("name-rev", "HEAD", "--name-only") != "master")
      sys.error("Publishing directory is not currently on the `master` branch!")

    log("[YELLOW]Checking if publishing directory is clean ...")
    if (git("status", "--porcelain").nonEmpty)
      sys.error("Publishing directory has uncommitted changes! Please commit before trying again!")

    log("[YELLOW]Checking if version directory already exists ...")
    if (versionDir.exists) log(versionDir.toString + " already exists, updating ...")
    else if (create) log(versionDir.toString + " doesn't exist yet, creating ...")
    else sys.error("Version directory '" + versionDir + "' doesn't exist yet, specify '-c' flag to create")

    val targetHtml = doMakeDocs("dirhtml")(baseDir, version, st)
    log("[YELLOW]Moving '" + targetHtml + "' to '" + versionDir + "' ...")
    if (!targetHtml.exists) sys.error(targetHtml.toString + " not found")
    IO.delete(versionDir)
    if (!targetHtml.renameTo(versionDir)) sys.error("Couldn't move '" + targetHtml + "' to '" + versionDir + "'")

    if (create) {
      log("[YELLOW]Refreshing releases.json ...")
      refreshReleasesJson(pubDir)
    }

    if (git("status", "--porcelain").isEmpty)
      sys.error("Publishing not required, no changes to doc-site detected")

    git("add", "-A")
    git("commit", "-m", (if (create) "Create" else "Update") + " /" + version)
    git("push", "origin", "master")
  }

  def refreshReleasesJson(pubDir: File) {
    val json = IO.listFiles(pubDir, DirectoryFilter).view
      .map(_.getName)
      .filterNot(_ == ".git")
      .map(""""%1$s": "/%1$s/"""".format(_))
      .:+(""""0.9": "https://github.com/spray/spray/wiki/"""")
      .mkString("{\n  ", ",\n  ", "\n}")
    IO.write(pubDir / "releases.json", json)
  }

  type Log = String => Unit

  def colorLog(state: State, level: Level.Value = Level.Info): Log = {
    val logger = CommandSupport.logger(state)
    if (logger.ansiCodesSupported) { msg =>
      logger.log(level, msg
        .replace("[RED]", RED)
        .replace("[GREEN]", GREEN)
        .replace("[YELLOW]", YELLOW)
        .replace("[BLUE]", BLUE)
        .replace("[MAGENTA]", MAGENTA)
        .replace("[CYAN]", CYAN)
        .replace("[WHITE]", WHITE))
    } else { msg =>
      logger.log(level, msg
        .replace("[RED]", "")
        .replace("[GREEN]", "")
        .replace("[YELLOW]", "")
        .replace("[BLUE]", "")
        .replace("[MAGENTA]", "")
        .replace("[CYAN]", "")
        .replace("[WHITE]", ""))
    }
  }

  def git(args: Any*)(implicit gitDir: File) = {
    val cmd =
      "git" +: "--git-dir" +: (gitDir.toString + "/.git") +: "--work-tree" +: gitDir.toString +: args.map(_.toString)
    println(cmd.mkString(" "))
    Process(cmd).!!.trim
  }

  // Opens uri in a browser if we are on a 1.6+ JVM
  private def openBrowser(uri: String) {
    val desktop = Class.forName("java.awt.Desktop")
    desktop
      .getMethod("browse", classOf[java.net.URI])
      .invoke(desktop.getMethod("getDesktop").invoke(null), new URI(uri))
  }
}
