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
import akka.actor.ActorSystem
import spray.util.ConnectionCloseReasons


class ConnectionTimeoutsSpec extends Specification with PipelineStageTest {
  val system = ActorSystem()
  val stage = ConnectionTimeouts(50, system.log)

  "The ConnectionTimeouts PipelineStage" should {
    "be transparent to unrelated commands" in {
      val cmd = new Command {}
      stage.test {
        val Commands(command) = process(cmd)
        command === cmd
      }
    }
    "be transparent to unrelated events" in {
      val ev = new Event {}
      stage.test {
        val Events(event) = process(ev)
        event === ev
      }
    }
    "upon a Tick, create a Close command if the idle timeout expired" in {
      stage.test {
        processAndClear(Received("Some Message"))
        Thread.sleep(60)
        val Commands(command) = process(TickGenerator.Tick)
        command === IOConnectionActor.Close(ConnectionCloseReasons.IdleTimeout)
      }
    }
    "reset the idle timer on Received events" in {
      stage.test {
        Thread.sleep(60)
        val Commands(commands@ _*) = process(
          Received("Some Message"),
          TickGenerator.Tick
        )
        commands must beEmpty
      }
    }
    "reset the idle timer on Send commands" in {
      stage.test {
        Thread.sleep(60)
        val Commands(command) = process(
          Send("Some Message"),
          TickGenerator.Tick
        )
        command === SendString("Some Message")
      }
    }
  }

  step(system.shutdown())
}
