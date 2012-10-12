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

import java.net.InetSocketAddress
import akka.actor._


trait ConnectionActors extends IOPeer {

  override protected def createConnectionHandle(_key: Key, _remoteAddress: InetSocketAddress,
                                                _localAddress: InetSocketAddress, _commander: ActorRef, _tag: Any) = {
    new Handle {
      val key = _key
      val remoteAddress = _remoteAddress
      val localAddress = _localAddress
      val commander = _commander
      val tag = _tag
      val handler = context.actorOf(Props(createConnectionActor(this))) // must be initialized last
    }
  }

  protected def createConnectionActor(handle: Handle): IOConnectionActor = new IOConnectionActor(handle)

  protected def pipeline: PipelineStage

  class IOConnectionActor(val handle: Handle) extends Actor {
    val pipelines = pipeline.build(
      context = createPipelineContext,
      commandPL = baseCommandPipeline,
      eventPL = baseEventPipeline
    )

    def createPipelineContext: PipelineContext = PipelineContext(handle, context)

    //# final-stages
    def baseCommandPipeline: Pipeline[Command] = {
      case IOPeer.Send(buffers, ack)          => ioBridge ! IOBridge.Send(handle, buffers, eventize(ack))
      case IOPeer.Close(reason)               => ioBridge ! IOBridge.Close(handle, reason)
      case IOPeer.StopReading                 => ioBridge ! IOBridge.StopReading(handle)
      case IOPeer.ResumeReading               => ioBridge ! IOBridge.ResumeReading(handle)
      case IOPeer.Tell(receiver, msg, sender) => receiver.tell(msg, sender)
      case _: Droppable => // don't warn
      case cmd => log.warning("commandPipeline: dropped {}", cmd)
    }

    def baseEventPipeline: Pipeline[Event] = {
      case x: IOPeer.Closed =>
        log.debug("Stopping connection actor, connection was closed due to {}", x.reason)
        context.stop(self)
        context.parent ! x // also inform our parent of our closing
      case _: Droppable => // don't warn
      case ev => log.warning("eventPipeline: dropped {}", ev)
    }
    //#

    def eventize(ack: Option[Any]) = ack match {
      case None | Some(_: Event) => ack
      case Some(x) => Some(IOPeer.AckEvent(x))
    }

    //# receive
    def receive = {
      case x: Command => pipelines.commandPipeline(x)
      case x: Event => pipelines.eventPipeline(x)
      case Status.Failure(x: CommandException) => pipelines.eventPipeline(x)
      case Terminated(actor) => pipelines.eventPipeline(IOPeer.ActorDeath(actor))
    }
    //#
  }
}