/*
 * Copyright (C) 2011-2012 spray.cc
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
import collection.mutable.ListBuffer


class PipelinesSpec extends Specification { def is =

  "The PipelineStage infrastructure must correctly combine the following pipeline stages:" ^
    "command >> command" ! example(cmd("1") >> cmd("2"), "x12+2,1 | +x")^
    "command >> event"   ! example(cmd("1") >> ev("a"), "x1+1 | a+xa")^
    "command >> double"  ! example(cmd("1") >> dbl("2", "a"), "x12+1 | +xa")^
    "command >> empty"   ! example(cmd("1") >> empty, "x1+1 | +x")^
    "event >> command"   ! example(ev("a") >> cmd("1"), "x1+1 | a+xa")^
    "event >> event"     ! example(ev("a") >> ev("b"), "x+ | b,a+xba")^
    "event >> double"    ! example(ev("a") >> dbl("1", "b"), "x1+ | a1+xba")^
    "event >> empty"     ! example(ev("a") >> empty, "x+ | a+xa")^
    "double >> command"  ! example(dbl("1", "a") >> cmd("2"), "x12+2a | +xa")^
    "double >> event"    ! example(dbl("1", "a") >> ev("b"), "x1+ | b+xba")^
    "double >> double"   ! example(dbl("1", "a") >> dbl("2", "b"), "x12+ | +xba")^
    "double >> empty"    ! example(dbl("1", "a") >> empty, "x1+ | +xa")^
    "empty >> command"   ! example(empty >> cmd("1"), "x1+1 | +x")^
    "empty >> event"     ! example(empty >> ev("a"), "x+ | a+xa")^
    "empty >> double"    ! example(empty >> dbl("1", "a"), "x1+ | +xa")^
    "empty >> empty"     ! example(empty >> empty, "x+ | +x")


  def example(stage: PipelineStage, expected: String) = {
    val cmdResult = ListBuffer.empty[String]
    val evResult = ListBuffer.empty[String]
    val pl = stage.buildPipelines(null,
      cmd => cmdResult += cmd.asInstanceOf[TestCommand].s,
      ev => evResult += ev.asInstanceOf[TestEvent].s
    )
    pl.commandPipeline(TestCommand("x"))
    val cmdTest = cmdResult.mkString(",") + '+' + evResult.mkString(",")
    cmdResult.clear()
    evResult.clear()
    pl.eventPipeline(TestEvent("x"))
    val evTest = cmdResult.mkString(",") + '+' + evResult.mkString(",")
    cmdTest + " | " + evTest === expected
  }

  def cmd(c: String) = new CommandPipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = { cmd =>
      commandPL(TestCommand(cmd.asInstanceOf[TestCommand].s + c))
      eventPL(TestEvent(c))
    }
  }

  def ev(e: String) = new EventPipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = { ev =>
      commandPL(TestCommand(e))
      eventPL(TestEvent(ev.asInstanceOf[TestEvent].s + e))
    }
  }

  def dbl(c: String, e: String) = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = Pipelines(
      commandPL = cmd => commandPL(TestCommand(cmd.asInstanceOf[TestCommand].s + c)),
      eventPL = ev => eventPL(TestEvent(ev.asInstanceOf[TestEvent].s + e))
    )
  }

  val empty = EmptyPipelineStage

  case class TestEvent(s: String) extends Event
  case class TestCommand(s: String) extends Command
}
