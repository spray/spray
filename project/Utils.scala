import sbt._
import com.decodified.scalassh.{Command => SshCommand, _}
import scala.Console.{RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE}


object Utils {
  type Log = String => Unit

  def job(body: => Boolean): Unit = {
    if (!body) sys.error("Did not succeed")
  }

  def runCommands(host: String, state: State, commands: SshCommand*): Boolean = {
    val log = colorLog(state)
    log("[YELLOW]Executing %s %s on %s:".format(commands.size, if (commands.size == 1) "command" else "commands", host))
    SSH(host, HostFileConfig()) { client =>
      commands.forall { command =>
        log("[CYAN]" + command.command)
        runCommand(client, state, command).map(_.exitCode).map {
          case Some(0) => true
          case Some(x) => state.log.warn("Exit code: " + x); false
          case None => state.log.warn("No exit code received"); false
        } getOrElse(false)
      }
    }.fold({ e => state.log.error(e); false }, identity)
  }

  def runCommand(client: SshClient, state: State, command: SshCommand): Option[CommandResult] = {
    val log = colorLog(state)
    client.exec(command) match {
      case Right(result) =>
        result.exitErrorMessage.foreach(msg => state.log.error("Exit error msg: " + msg))
        val stdErr = result.stdErrAsString()
        if (stdErr.nonEmpty) log("[RED]" + stdErr)
        val stdOut = result.stdOutAsString()
        if (stdOut.nonEmpty) log(stdOut)
        Some(result)
      case Left(error) =>
        state.log.error(error)
        None
    }
  }

  def resourceContent(resource: String) =
    new StreamCopier().emptyToString(getClass.getClassLoader.getResourceAsStream(resource))

  def sudoCreateFile(file: String, input: CommandInput) = {
    val cmd = createFile(file, input)
    cmd.copy(command = "sudo " + cmd.command)
  }

  def createFile(file: String, input: CommandInput) =
    SshCommand("tee " + file + " > /dev/null", input)

  def sudoRestartService(service: String) =
    SshCommand("sudo invoke-rc.d " + service + " restart")

  def git(args: Any*) = Process("git" +: args.map(_.toString)).!!.trim

  def colorLog(state: State, level: Level.Value = Level.Info): Log = {
    val logger = state.log
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

  // an SBT AbstractLogger that logs to /dev/nul
  object NopLogger extends AbstractLogger {
    def getLevel = Level.Error
    def setLevel(newLevel: Level.Value): Unit = {}
    def setTrace(flag: Int): Unit = {}
    def getTrace = 0
    def successEnabled = false
    def setSuccessEnabled(flag: Boolean): Unit = {}
    def control(event: ControlEvent.Value, message: => String): Unit = {}
    def logAll(events: Seq[LogEvent]): Unit = {}
    def trace(t: => Throwable): Unit = {}
    def success(message: => String): Unit = {}
    def log(level: Level.Value, message: => String): Unit = {}
  }
}
