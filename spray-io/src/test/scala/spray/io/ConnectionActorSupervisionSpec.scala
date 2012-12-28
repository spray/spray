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

import scala.concurrent.duration.Duration
import org.specs2.mutable.Specification
import akka.actor.{ActorRef, Props, ActorSystem}
import akka.util.Timeout
import akka.pattern.ask
import spray.util._


class ConnectionActorSupervisionSpec extends Specification  {
  import IOClientConnectionActor._
  implicit val timeout: Timeout = Duration(1, "sec") // for asks below
  implicit val system = ActorSystem()
  val port = 23556

  installDebuggingEventStreamLoggers()
  sequential

  step {
    system.actorOf(Props(new TestServer), name = "test-server").ask(
      IOServer.Bind("localhost", port, tag = LogMark("SERVER"))
    ).await
  }

  "Server-side Connection Actors" should {
    val client = system.actorOf(Props(new IOClientConnectionActor()), name = "test-client-1")

    "be able to run their pipeline normally" in {
      val responseF = for {
        Connected(_, _) <- client ? Connect("localhost", port, tag = LogMark("CLIENT"))
        Received(_, buffer) <- client ? send("ECHO")
      } yield buffer.drainToString
      responseF.await === "ECHO"
    }

    "be stopped and their connection closed in case of pipeline exceptions" in {
      val closeReason = for {
        Closed(_, reason) <- client ? send("CRASH")
      } yield reason
      closeReason.await === ConnectionCloseReasons.PeerClosed
    }
  }

  step(system.shutdown())

  def send(string: String) = Send(BufferBuilder(string).toByteBuffer)

  class TestServer extends IOServer with ConnectionActors {
    val pipelineStage = new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL) = new Pipelines {
        val commandPipeline = commandPL
        val eventPipeline: EPL = {
          case Received(connection, buffer) if buffer.duplicate.drainToString == "CRASH" => sys.error("Crash Boom Bang")
          case Received(connection, buffer) => commandPipeline(Send(buffer))
        }
      }
    }
    def createConnectionActor(connection: Connection): ActorRef =
      context.actorOf(Props(new DefaultIOConnectionActor(connection, pipelineStage)), nextConnectionActorName)
  }

}
