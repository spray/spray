import sbt._
import scala.Console.{RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE}


object Utils {
  type Log = String => Unit

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
    def setLevel(newLevel: Level.Value) {}
    def setTrace(flag: Int) {}
    def getTrace = 0
    def successEnabled = false
    def setSuccessEnabled(flag: Boolean) {}
    def control(event: ControlEvent.Value, message: => String) {}
    def logAll(events: Seq[LogEvent]) {}
    def trace(t: => Throwable) {}
    def success(message: => String) {}
    def log(level: Level.Value, message: => String) {}
  }
}
