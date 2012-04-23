/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.io

import org.specs2.Specification

class PipelinesSpec extends Specification { def is =

  "The PipelineStage infrastructure must correctly combine the following pipeline stages:" ^
    "command ~> command" ! example(cmd('1') ~> cmd('2'), "12", "")^
    "command ~> event"   ! example(cmd('1') ~> ev('a'), "1", "a")^
    "command ~> double"  ! example(cmd('1') ~> dbl('2', 'a'), "12", "a")^
    "command ~> empty"   ! example(cmd('1') ~> empty, "1", "")^
    "event ~> command"   ! example(ev('a') ~> cmd('1'), "1", "a")^
    "event ~> event"     ! example(ev('a') ~> ev('b'), "", "ba")^
    "event ~> double"    ! example(ev('a') ~> dbl('1', 'b'), "1", "ba")^
    "event ~> empty"     ! example(ev('a') ~> empty, "", "a")^
    "double ~> command"  ! example(dbl('1', 'a') ~> cmd('2'), "12", "a")^
    "double ~> event"    ! example(dbl('1', 'a') ~> ev('b'), "1", "ba")^
    "double ~> double"   ! example(dbl('1', 'a') ~> dbl('2', 'b'), "12", "ba")^
    "double ~> empty"    ! example(dbl('1', 'a') ~> empty, "1", "a")^
    "empty ~> command"   ! example(empty ~> cmd('1'), "1", "")^
    "empty ~> event"     ! example(empty ~> ev('a'), "", "a")^
    "empty ~> double"    ! example(empty ~> dbl('1', 'a'), "1", "a")^
    "empty ~> empty"     ! example(empty ~> empty, "", "")


  def example(stage: PipelineStage, cmdExp: String, evExp: String) = {
    def toOption(s: String) = if (s.isEmpty) None else Some(s)
    var cmdResult: Option[String] = None
    var evResult: Option[String] = None
    val pl = stage.buildPipelines(null,
      cmd => cmdResult = toOption(cmd.asInstanceOf[TestCommand].s),
      ev => evResult = toOption(ev.asInstanceOf[TestEvent].s)
    )
    pl.commandPipeline(TestCommand(""))
    pl.eventPipeline(TestEvent(""))
    (cmdResult === toOption(cmdExp)) and (evResult === toOption(evExp))
  }

  def cmd(c: Char) = new CommandPipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      cmd => commandPL(TestCommand(cmd.asInstanceOf[TestCommand].s + c))
  }

  def ev(e: Char) = new EventPipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      ev => eventPL(TestEvent(ev.asInstanceOf[TestEvent].s + e))
  }

  def dbl(c: Char, e: Char) = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = Pipelines(
      commandPL = cmd => commandPL(TestCommand(cmd.asInstanceOf[TestCommand].s + c)),
      eventPL = ev => eventPL(TestEvent(ev.asInstanceOf[TestEvent].s + e))
    )
  }

  val empty = EmptyPipelineStage

  case class TestEvent(s: String) extends Event
  case class TestCommand(s: String) extends Command
}
