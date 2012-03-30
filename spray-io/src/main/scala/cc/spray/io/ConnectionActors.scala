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

import java.net.InetSocketAddress
import akka.actor.{Status, PoisonPill, Props, Actor}


trait ConnectionActors extends IoPeer {

  override protected def createConnectionHandle(theKey: Key, theAddress: InetSocketAddress) = new Handle {
    val key = theKey
    val handler = context.actorOf(Props(createConnectionActor(this)))
    val address = theAddress
  }

  protected def createConnectionActor(handle: Handle): IoConnectionActor = new IoConnectionActor(handle)

  protected def pipeline: PipelineStage

  class IoConnectionActor(val handle: Handle) extends Actor {
    private[this] lazy val pipelines = pipeline.buildPipelines(
      context = PipelineContext(handle, context),
      commandPL = baseCommandPipeline,
      eventPL = baseEventPipeline
    )

    protected def baseCommandPipeline: Pipeline[Command] = {
      case x: IoPeer.Send => ioWorker ! IoWorker.Send(handle, x.buffers)
      case x: IoPeer.Close => ioWorker ! IoWorker.Close(handle, x.reason)
      case IoPeer.StopReading => ioWorker ! IoWorker.StopReading(handle)
      case IoPeer.ResumeReading => ioWorker ! IoWorker.ResumeReading(handle)
      case x: IoPeer.Tell => x.receiver.tell(x.message, x.sender)
      case _: Droppable => // don't warn
      case x => log.warning("commandPipeline: dropped {}", x)
    }

    protected def baseEventPipeline: Pipeline[Event] = {
      case x: IoPeer.Closed =>
        log.debug("Stopping connection actor, connection was closed due to {}", x.reason)
        self ! PoisonPill
      case _: Droppable => // don't warn
      case x => log.warning("eventPipeline: dropped {}", x)
    }

    protected def receive = {
      case x: Command => pipelines.commandPipeline(x)
      case x: Event => pipelines.eventPipeline(x)
      case Status.Failure(x: CommandException) => pipelines.eventPipeline(x)
    }
  }

}