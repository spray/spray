/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import akka.util.duration._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.io.Tcp
import akka.util.ByteString
import spray.testkit.Specs2PipelineStageTest
import akka.io.Tcp.CommandFailed

class ConnectionTimeoutsSpec extends Specification with Specs2PipelineStageTest with NoTimeConversions {
  val stage = ConnectionTimeouts(200.millis)

  val testData = ByteString("Some Message")

  "The ConnectionTimeouts PipelineStage" should {

    "be transparent to unrelated commands" in new Fixture(stage) {
      val cmd = new Command {}
      connectionActor ! cmd
      commands.expectMsg(cmd)
    }

    "be transparent to unrelated events" in new Fixture(stage) {
      val ev = new Event {}
      connectionActor ! ev
      events.expectMsg(ev)
    }

    "upon a Tick, create a Close command if the idle timeout expired" in new Fixture(stage) {
      connectionActor ! Tcp.Received(testData)
      Thread.sleep(210)
      connectionActor ! TickGenerator.Tick
      commands.expectMsg(Tcp.Abort)
    }

    "reset the idle timer on Received events" in new Fixture(stage) {
      Thread.sleep(210)
      connectionActor ! Tcp.Received(testData)
      connectionActor ! TickGenerator.Tick
      commands.expectNoMsg(100.millis)
    }

    "start the idle timer when no writes are pending any more" in new Fixture(stage) {
      Thread.sleep(210)
      object customAck extends Tcp.Event
      val write = Tcp.Write(testData)
      connectionActor ! write
      connectionActor ! TickGenerator.Tick
      commands.expectMsg(write)
      commands.expectNoMsg(210.millis)
      connectionActor ! TickGenerator.Tick
      commands.expectMsg(ConnectionTimeouts.TestWrite)
      commands.expectNoMsg(100.millis)

      // former write is still pending
      connectionActor ! CommandFailed(ConnectionTimeouts.TestWrite)
      commands.expectNoMsg(210.millis)
      connectionActor ! TickGenerator.Tick
      commands.expectMsg(ConnectionTimeouts.TestWrite)
      commands.expectNoMsg(100.millis)
      connectionActor ! ConnectionTimeouts.NoWritePending
      commands.expectNoMsg(210.millis)
      connectionActor ! TickGenerator.Tick
      commands.expectMsg(Tcp.Abort)
    }
  }
}
