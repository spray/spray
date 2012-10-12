/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.io

import org.specs2.mutable.Specification


class PipelinesSpec extends Specification {

  "The >> must correctly combine two PipelineStages" >> {
    val a = new TestStage('A')
    val b = new TestStage('B')
    val c = a >> b

    "example-1" in {
      test(c, TestCommand(".")) === (".AB", "")
    }
    "example-2" in {
      test(c, TestEvent(".")) === ("", ".BA")
    }
  }

  def test(stage: PipelineStage, cmdOrEv: AnyRef) = {
    var commandResult: String = ""
    var eventResult: String = ""
    val pl = stage.build(
      context = null,
      commandPL = { case TestCommand(s) => commandResult = s },
      eventPL = { case TestEvent(s) => eventResult = s }
    )
    cmdOrEv match {
      case cmd: Command => pl.commandPipeline(cmd)
      case ev: Event => pl.eventPipeline(ev)
    }
    (commandResult, eventResult)
  }

  class TestStage(c: Char) extends PipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL) =
      new Pipelines {
        val commandPipeline: CPL = {
          case TestCommand(s) => commandPL(TestCommand(s + c))
        }
        val eventPipeline: EPL = {
          case TestEvent(s) => eventPL(TestEvent(s + c))
        }
      }
  }

  case class TestEvent(s: String) extends Event
  case class TestCommand(s: String) extends Command
}
