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
import akka.actor.{Status, ActorRef}


trait IOClientConnection extends IOConnection {
  import IOClientConnection._

  private[this] var _pipelines: Pipelines = Pipelines.Uninitialized
  private[this] var _connection: Connection = _
  private[this] var _preConnect = true

  // override with custom pipeline stage construction
  def pipelineStage: PipelineStage = DefaultPipelineStage

  def pipelines: Pipelines = _pipelines

  def connection: Connection =
    if (_connection != null) _connection
    else throw new IllegalStateException("Not yet connected")

  override def isConnected: Boolean = _connection != null && super.isConnected

  def isPreConnect = _preConnect

  override def receive: Receive = {
    case cmd: Connect =>
      IOExtension(context.system).ioBridge() ! cmd
      _preConnect = false
      context.become(connecting(sender))
  }

  def connecting(commander: ActorRef): Receive = {
    case IOBridge.Connected(key, tag) =>
      _connection = createConnection(key, tag)
      _pipelines = createPipelines(_connection)
      sender ! IOBridge.Register(_connection)
      context.become(connected)
      // finally we send the Connected event on to the commander, however, we don't do it directly
      // but go through the pipeline (and thus give the stages a chance to inject custom logic)
      self ! Tell(commander, Connected(_connection), self)

    case Status.Failure(CommandException(cmd: Connect, cause)) => cantConnect(commander, cmd, cause)
  }

  def connected: Receive = super.receive

  def createConnection(_key: IOBridge.Key, _tag: Any): Connection =
    new Connection {
      val key = _key
      val tag = _tag
      val handler = self
    }

  def cantConnect(commander: ActorRef, cmd: Connect, cause: Throwable) {
    commander ! Status.Failure(new ConnectException(cmd.remoteAddress, cause))
    context.stop(self)
  }
}

object IOClientConnection {

  // The default PipelineStage implements a very simple command/event frontend
  // that dispatches incoming events to the sender of the last command
  object DefaultPipelineStage extends PipelineStage {
    def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
      new Pipelines {
        var commander: ActorRef = _

        val commandPipeline: CPL = {
          case cmd =>
            commander = context.sender
            commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case ev: Received if commander != null => commander ! ev
          case ev =>
            if (commander != null) commander ! ev
            eventPL(ev)
        }
      }
  }

  case class ConnectException(remoteAddress: InetSocketAddress, cause: Throwable)
    extends RuntimeException("Couldn't connect to " + remoteAddress, cause)

  ////////////// COMMANDS //////////////
  type Connect      = IOBridge.Connect;   val Connect = IOBridge.Connect
  type Close        = IOConnection.Close; val Close   = IOConnection.Close
  type Send         = IOConnection.Send;  val Send    = IOConnection.Send
  type Tell         = IOConnection.Tell;  val Tell    = IOConnection.Tell
  val StopReading   = IOConnection.StopReading
  val ResumeReading = IOConnection.ResumeReading

  ////////////// EVENTS //////////////
  case class Connected(connection: Connection) extends Event
  type Closed     = IOConnection.Closed;     val Closed     = IOConnection.Closed
  type Received   = IOConnection.Received;   val Received   = IOConnection.Received
  type AckEvent   = IOConnection.AckEvent;   val AckEvent   = IOConnection.AckEvent
  type ActorDeath = IOConnection.ActorDeath; val ActorDeath = IOConnection.ActorDeath
}