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
import akka.actor.{ActorRef, Status, Props, Actor}


trait ConnectionActors extends IoPeer { ioPeer =>

  override protected def createConnectionHandle(theKey: Key, theAddress: InetSocketAddress, theCommander: ActorRef) = {
    new Handle {
      val key = theKey
      val handler = context.actorOf(Props(createConnectionActor(this)))
      val address = theAddress
      val commander = theCommander
    }
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
      case IoPeer.Send(buffers, ack)          => ioWorker ! IoWorker.Send(handle, buffers, ack)
      case IoPeer.Close(reason)               => ioWorker ! IoWorker.Close(handle, reason)
      case IoPeer.StopReading                 => ioWorker ! IoWorker.StopReading(handle)
      case IoPeer.ResumeReading               => ioWorker ! IoWorker.ResumeReading(handle)
      case IoPeer.Tell(receiver, msg, sender) => receiver.tell(msg, sender)
      case _: Droppable => // don't warn
      case cmd => log.warning("commandPipeline: dropped {}", cmd)
    }

    protected def baseEventPipeline: Pipeline[Event] = {
      case x: IoPeer.Closed =>
        log.debug("Stopping connection actor, connection was closed due to {}", x.reason)
        context.stop(self)
        ioPeer.self ! x // inform our owner of our closing
      case _: Droppable => // don't warn
      case ev => log.warning("eventPipeline: dropped {}", ev)
    }

    protected def receive = {
      case x: Command => pipelines.commandPipeline(x)
      case x: Event => pipelines.eventPipeline(x)
      case Status.Failure(x: CommandException) => pipelines.eventPipeline(x)
    }
  }

}