package cc.spray.io

import org.specs2.Specification
import akka.actor.ActorContext

class PipelinesSpec extends Specification { def is =

  "The PipelineStage infrastructure must correctly combine the following pipeline stages:" ^
    "event ~> event"              ! example(ev('a') ~> ev('b'), "ba")^
    "event ~> command"            ! example(ev('a') ~> cmd('1'), "1", "a")^
    "event ~> double"             ! example(ev('a') ~> dbl('1', 'b'), "1", "ba")^
    "command ~> event"            ! example(cmd('1') ~> ev('a'), "1", "a")^
    "command ~> command"          ! example(cmd('1') ~> cmd('2'), "12")^
    "command ~> double"           ! example(cmd('1') ~> dbl('2', 'a'), "12", "a")^
    "double ~> event"             ! example(dbl('1', 'a') ~> ev('b'), "1", "ba")^
    "double ~> command"           ! example(dbl('1', 'a') ~> cmd('2'), "12", "a")^
    "double ~> double"            ! example(dbl('1', 'a') ~> dbl('2', 'b'), "12", "ba")

  def example(stage: DoublePipelineStage, cmdExp: String, evExp: String) = {
    var cmdResult: Option[String] = None
    var evResult: Option[String] = None
    val pl = stage.build(null,
      cmd => cmdResult = Some(cmd.asInstanceOf[TestCommand].s),
      ev => evResult = Some(ev.asInstanceOf[TestEvent].s)
    )
    pl.commandPipeline(TestCommand(""))
    pl.eventPipeline(TestEvent(""))
    (cmdResult === Some(cmdExp)) and (evResult === Some(evExp))
  }

  def example(stage: CommandPipelineStage, expected: String) = {
    var result: Option[String] = None
    val pl = stage.build(null, cmd => result = Some(cmd.asInstanceOf[TestCommand].s))
    pl(TestCommand(""))
    result === Some(expected)
  }

  def example(stage: EventPipelineStage, expected: String) = {
    var result: Option[String] = None
    val pl = stage.build(null, ev => result = Some(ev.asInstanceOf[TestEvent].s))
    pl(TestEvent(""))
    result === Some(expected)
  }

  def dbl(c: Char, e: Char) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = Pipelines(
      commandPL = cmd => commandPL(TestCommand(cmd.asInstanceOf[TestCommand].s + c)),
      eventPL = ev => eventPL(TestEvent(ev.asInstanceOf[TestEvent].s + e))
    )
  }

  def cmd(c: Char) = new CommandPipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command]) =
      cmd => commandPL(TestCommand(cmd.asInstanceOf[TestCommand].s + c))
  }

  def ev(e: Char) = new EventPipelineStage {
    def build(context: ActorContext, eventPL: Pipeline[Event]) =
      ev => eventPL(TestEvent(ev.asInstanceOf[TestEvent].s + e))
  }

  case class TestEvent(s: String) extends Event
  case class TestCommand(s: String) extends Command

}
