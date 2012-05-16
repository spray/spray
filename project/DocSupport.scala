import sbt._
import Keys._
import scala.Console.{RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE}

trait DocSupport {
  def sprayBase: Project
  def sprayCan: Project
  def sprayClient: Project
  def sprayIo: Project
  def sprayServer: Project
  def sprayUtil: Project

  val docVersion = SettingKey[String]("doc-version", "The version of the produced documentation")
  val docTarget = SettingKey[String]("doc-target", "Either 'html' or 'dirhtml'")
  val doMakeDocs = TaskKey[File]("do-make-docs", "Runs `make clean html` in the /docs directory")
  val doMoveScaladoc = TaskKey[File]("do-move-scaladoc", "Moves the freshly created scaladocs into the site repo")
  val siteDir = TaskKey[File]("site-dir", "Finds, verifies and returns the directory of the site repo")

  val docSupportSettings = seq(
    docVersion := "latest",
    docTarget := "html",

    commands += Command.args("make-docs", "[-o]") { (state, args) =>
      val tempState = reapply(state, docVersion := "latest", docTarget := "html")
      val extracted = Project.extract(tempState)
      val (_, result) = extracted.runTask(doMakeDocs, tempState)
      if (args == Seq("-o")) openBrowser("file://localhost" + result + "/index.html")
      state
    },

    doMakeDocs <<= (baseDirectory, docVersion, docTarget, state) map { (baseDir, docVersion, docTarget, state) =>
      val docsDir = baseDir / "/docs"
      val log = colorLog(state)
      log("[YELLOW]Rebuilding doc site at " + docsDir)
      val exitCode = Process {
        "make" ::
        "-C" :: docsDir.toString ::
        "clean" :: docTarget ::
        "SPHINXOPTS = -D version=%1$s -D release=%1$s".format(docVersion) :: Nil
      } ! CommandSupport.logger(state)
      if (exitCode != 0) sys.error("Error making the doc site")

      val htmlDir = docsDir / "target" / docTarget
      IO.listFiles(htmlDir, DirectoryFilter).filter(_.getName.startsWith("spray-")).foreach { dir =>
        val index = dir / "index.html"
        log("[YELLOW]Patching '" + index + "' ...")
        assert(index.exists)
        IO.writeLines(index, IO.readLines(index).map(_.replace("_version_", docVersion)))
      }
      htmlDir
    },

    commands += Command.single("move-scaladoc") { (state, arg) =>
      val tempState = reapply(state, docVersion := arg)
      val extracted = Project.extract(tempState)
      extracted.runTask(doMoveScaladoc, tempState)
      state
    },

    doMoveScaladoc <<= (siteDir, docVersion, state,
      doc in (sprayBase, Compile),
      doc in (sprayCan, Compile),
      doc in (sprayClient, Compile),
      doc in (sprayIo, Compile),
      doc in (sprayServer, Compile),
      doc in (sprayUtil, Compile)
    ) map { (siteDir, version, state, docBase, docCan, docClient, docIo, docServer, docUtil) =>
      val log = colorLog(state)
      def move(docDir: File) {
        assert(docDir.getName == "api")
        assert(docDir.getParentFile.getName == "target")
        val projectName = docDir.getParentFile.getParentFile.getName
        val versionDir = siteDir / "api" / projectName / version
        log("[YELLOW]Moving '" + docDir + "' to '" + versionDir + "' ...")
        IO.createDirectory(versionDir.getParentFile)
        IO.delete(versionDir)
        if (!docDir.renameTo(versionDir)) sys.error("Couldn't move '" + docDir + "' to '" + versionDir + "'")
      }
      List(docBase, docCan, docClient, docIo, docServer, docUtil) foreach move
      siteDir
    },

    commands += Command.single("update-docs") { (state, arg) =>
      val log = colorLog(state)
      val tempState = reapply(state, docVersion := arg, docTarget := "dirhtml")
      val extracted = Project.extract(tempState)
      val (_, htmlDir) = extracted.runTask(doMakeDocs, tempState)
      val (_, siteDir) = extracted.runTask(doMoveScaladoc, tempState)
      val versionDir = siteDir / arg
      log("[YELLOW]Moving '" + htmlDir + "' to '" + versionDir + "' ...")
      if (!versionDir.exists) log("[MAGENTA]'" + versionDir + "' doesn't exist yet, creating ...")
      else IO.delete(versionDir)
      if (!htmlDir.renameTo(versionDir)) sys.error("Couldn't move '" + htmlDir + "' to '" + versionDir + "'")

      log("[YELLOW]Refreshing releases.json ...")
      val json = IO.listFiles(siteDir, DirectoryFilter).view
        .map(_.getName)
        .filterNot(x => x == ".git" || x == "api")
        .map(""""%1$s": "/%1$s/"""".format(_))
        .:+(""""0.9": "https://github.com/spray/spray/wiki/"""")
        .sorted(Ordering.String.reverse)
        .mkString("{\n  ", ",\n  ", "\n}")
      IO.write(siteDir / "releases.json", json)

      log("[GREEN]Update of '" + siteDir + "' succeeded, ready for committing and pushing ...")
      state
    },

    siteDir <<= (baseDirectory, state) map { (baseDir, state) =>
      implicit val dir = Path(baseDir).asFile.getParentFile / "spray.github.com"
      val log = colorLog(state)

      log("[YELLOW]Checking if publishing directory '" + dir + "' exists ...")
      if (!dir.exists)
        sys.error("Cannot find publishing directory " + dir)

      log("[YELLOW]Checking if publishing directory is a GIT repo ...")
      if (!(dir / ".git").exists)
        sys.error("Not a git repository " + dir)

      log("[YELLOW]Checking if publishing directory is currently on the 'master' branch ...")
      if (git("name-rev", "HEAD", "--name-only") != "master")
        sys.error("Publishing directory is not currently on the `master` branch!")

      dir
    }
  )

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

  private def reapply(state: State, settings: Setting[_]*) = {
    val extracted = Project.extract(state)
    import extracted._
    val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)

    // We don't want to be able to save the settings that are applied herewith.
    // Just using an empty string works fine and in case the user calls `session save`, empty lines will be generated.
    val newSession = session.appendSettings(append.map((_, "")))
    BuiltinCommands.reapply(newSession, structure, state)
  }
}
