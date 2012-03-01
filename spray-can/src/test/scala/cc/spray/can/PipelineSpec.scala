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

package cc.spray.can

import akka.testkit.TestActorRef
import akka.actor.{Actor, ActorSystem}
import collection.mutable.ListBuffer
import java.nio.ByteBuffer
import cc.spray.io._
import akka.event.BusLogging
import org.specs2.Specification

abstract class PipelineSpec(name: String) extends Specification {

  implicit val system = ActorSystem(name)
  val connectionActor = TestActorRef(new DummyActor('connectionActor))
  val singletonHandler = TestActorRef(new DummyActor('singletonHandler))
  val log = new BusLogging(system.eventStream, name, getClass)

  class DummyActor(name: Symbol) extends Actor {
    def this(name: String) = this(Symbol(name))
    protected def receive = {
      case 'name => sender ! name
    }
    def theContext = context
  }

  def received(rawMessage: String) = IoWorker.Received(
    SimpleHandle(null, connectionActor),
    ByteBuffer.wrap(prepareString(rawMessage).getBytes("US-ASCII"))
  )

  def send(rawMessage: String): Command = SendString(prepareString(rawMessage))

  def produceOneCommand(command: Command) = {
    beEqualTo(List(command) -> Nil) ^^ { result: TestPipelineResult =>
      result._1.map {
        case IoPeer.Send(buffers) => SendString(buffers.map(buf => new String(buf.array, "US-ASCII")).mkString)
        case x => x
      } -> result._2
    }
  }

  private def prepareString(s: String) = s.stripMargin.replace("\n", "\r\n")

  private case class SendString(string: String) extends Command

  type TestPipelineResult = (List[Command], List[Event])

  class TestPipeline(pipeline: DoublePipelineStage) {
    private val collectedCommands = ListBuffer.empty[Command]
    private val collectedEvents = ListBuffer.empty[Event]
    private val pipelines = pipeline.build(connectionActor.underlyingActor.theContext, collectedCommands.append(_),
      collectedEvents.append(_))

    def runCommands(commands: Command*): TestPipelineResult = {
      clear()
      commands.foreach(pipelines.commandPipeline)
      (collectedCommands.toList, collectedEvents.toList)
    }

    def runEvents(events: Event*): TestPipelineResult = {
      clear()
      events.foreach(pipelines.eventPipeline)
      (collectedCommands.toList, collectedEvents.toList)
    }

    private def clear() {
      collectedCommands.clear()
      collectedEvents.clear()
    }
  }

}
