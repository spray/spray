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

import akka.actor._


trait ConnectionActors extends IOPeer {

  override protected def createConnectionHandle(_key: IOBridge.Key, _ioBridge: ActorRef,
                                                _commander: ActorRef, _tag: Any): Connection = {
    new Connection {
      val key = _key
      val ioBridge = _ioBridge
      val commander = _commander
      val tag = _tag
      val handler = createConnectionActor(this) // must be last member to be initialized
    }
  }

  protected def createConnectionActor(connection: Connection): ActorRef =
    context.actorOf(Props(new IOConnectionActor(connection)))

  protected def pipeline: PipelineStage

  class IOConnectionActor(val connection: Connection) extends Actor {
    import connection.ioBridge

    val pipelines = pipeline.build(
      context = createPipelineContext,
      commandPL = baseCommandPipeline,
      eventPL = baseEventPipeline
    )

    def createPipelineContext: PipelineContext = PipelineContext(connection, context)

    //# final-stages
    def baseCommandPipeline: Pipeline[Command] = {
      case IOPeer.Send(buffers, ack)          => ioBridge ! IOBridge.Send(connection, buffers, eventize(ack))
      case IOPeer.Close(reason)               => ioBridge ! IOBridge.Close(connection, reason)
      case IOPeer.StopReading                 => ioBridge ! IOBridge.StopReading(connection)
      case IOPeer.ResumeReading               => ioBridge ! IOBridge.ResumeReading(connection)
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