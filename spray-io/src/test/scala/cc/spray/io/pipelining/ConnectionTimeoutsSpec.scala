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

package cc.spray.io.pipelining

import org.specs2.mutable.Specification
import cc.spray.io.{IOPeer, IdleTimeout, Event, Command}


class ConnectionTimeoutsSpec extends Specification with PipelineStageTest {
  val fixture = new Fixture(ConnectionTimeouts(50, system.log))

  "The ConnectionTimeouts PipelineStage" should {
    "be transparent to unrelated commands" in {
      val command = new Command {}
      fixture(command).commands === Seq(command)
    }
    "be transparent to unrelated events" in {
      val event = new Event {}
      fixture(event).events === Seq(event)
    }
    "upon a Tick, create a Close command if the idle timeout expired" in {
      fixture(
        Received("Some Message"),
        Sleep("60 ms"),
        TickGenerator.Tick
      ).commands === Seq(IOPeer.Close(IdleTimeout))
    }
    "reset the idle timer on Received events" in {
      fixture(
        Sleep("50 ms"),
        Received("Some Message"),
        TickGenerator.Tick
      ).commands === Seq()
    }
    "reset the idle timer on Send commands" in {
      fixSends {
        fixture(
          Sleep("50 ms"),
          Send("Some Message"),
          TickGenerator.Tick
        ).commands
      } === Seq(SendString("Some Message"))
    }
  }

  step {
    cleanup()
  }
}
