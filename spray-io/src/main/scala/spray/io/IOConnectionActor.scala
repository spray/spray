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

import java.nio.ByteBuffer
import akka.actor.{ActorRef, Terminated, Status, Actor}
import akka.event.Logging
import spray.util.{CloseCommandReason, SprayActorLogging, ConnectionCloseReasons}


trait IOConnectionActor extends Actor with SprayActorLogging {
  import IOConnectionActor._

  def connection: Connection

  def pipelines: Pipelines

  def createPipelines(connection: Connection, pipelineStage: PipelineStage): Pipelines =
    pipelineStage.build(createPipelineContext(connection), baseCommandPipeline, baseEventPipeline)

  def createPipelineContext(connection: Connection): PipelineContext =
    new DefaultPipelineContext(connection, context)

  def ioBridge = connection.key.ioBridge

  def connected = connection.connected

  // we don't keep the TaggableLogs as fields since they are either
  // needed only once (after connection close) or rarely
  def debug = TaggableLog(log, Logging.DebugLevel)
  def warn = TaggableLog(log, Logging.WarningLevel)

  //# final-stages
  def baseCommandPipeline: Pipeline[Command] = {
    case Send(buffers, ack)          => ioBridge ! IOBridge.Send(connection, buffers, eventize(ack))
    case Close(reason)               => ioBridge ! IOBridge.Close(connection, reason)
    case StopReading                 => ioBridge ! IOBridge.StopReading(connection)
    case ResumeReading               => ioBridge ! IOBridge.ResumeReading(connection)
    case Tell(receiver, msg, sender) => receiver.tell(msg, sender)
    case _: Droppable => // don't warn
    case cmd => warn.log(connection.tag, "commandPipeline: dropped {}", cmd)
  }

  def baseEventPipeline: Pipeline[Event] = {
    case x: Closed =>
      // by default the final stage of the event pipeline simply stops the connection actor
      // when the connection has been closed for whatever reason, an earlier (custom) stage
      // of the event pipeline can however decide to let the actor die with an exception in
      // order to make use of supervision for dealing with unexpected connection closings
      debug.log(connection.tag, "Stopping connection actor, connection was closed due to {}", x.reason)
      context.stop(self)

    case _: Droppable => // don't warn
    case ev => warn.log(connection.tag, "eventPipeline: dropped {}", ev)
  }
  //#

  //# receive
  def receive: Receive = {
    case x: Command                          => pipelines.commandPipeline(x)
    case x: Event                            => pipelines.eventPipeline(x)
    case Status.Failure(x: CommandException) => pipelines.eventPipeline(x)
    case Terminated(actor)                   => pipelines.eventPipeline(ActorDeath(actor))
  }
  //#

  def eventize(ack: Option[Any]) = ack match {
    case None | Some(_: Event) => ack
    case Some(x)               => Some(AckEvent(x))
  }

  override def postStop() {
    // if the connection hasn't been closed yet we have been irregularly stopped by our supervisor
    // therefore we need to clean up our connection here
    if (connected) ioBridge ! IOBridge.Close(connection, ConnectionCloseReasons.InternalError)
  }
}

object IOConnectionActor {

  ////////////// COMMANDS //////////////
  case class Close(reason: CloseCommandReason) extends Command
  case class Send(buffers: Seq[ByteBuffer], ack: Option[Any]) extends Command
  object Send {
    def apply(buffer: ByteBuffer): Send = apply(buffer, None)
    def apply(buffer: ByteBuffer, ack: Option[Any]): Send = new Send(buffer :: Nil, ack)
  }
  case class Tell(receiver: ActorRef, message: Any, sender: ActorRef) extends Command

  case object StopReading extends Command
  case object ResumeReading extends Command

  ////////////// EVENTS //////////////
  type Closed = IOBridge.Closed;     val Closed = IOBridge.Closed
  type Received = IOBridge.Received; val Received = IOBridge.Received

  case class ActorDeath(actor: ActorRef) extends Event
  case class AckEvent(ack: Any) extends Event
}

class DefaultIOConnectionActor(val connection: Connection, pipelineStage: PipelineStage) extends IOConnectionActor {
  val pipelines = createPipelines(connection, pipelineStage)
}