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
import IOClientConnectionActor.DefaultPipelineStage


class IOClientConnectionActor(pipelineStage: PipelineStage = DefaultPipelineStage) extends IOConnectionActor {
  import IOClientConnectionActor._

  private[this] var _pipelines: Pipelines = Pipelines.Uninitialized
  private[this] var _connection: Connection = _

  def pipelines: Pipelines = _pipelines

  def connection: Connection =
    if (_connection != null) _connection
    else throw new IllegalStateException("Not yet connected")

  override def connected: Boolean = _connection != null && super.connected

  override def receive: Receive = {
    case cmd: Connect =>
      IOExtension(context.system).ioBridge() ! cmd
      context.become(connecting(sender))
  }

  def connecting(commander: ActorRef): Receive = {
    case ev@ IOBridge.Connected(key, tag) =>
      _connection = createConnection(key, tag)
      _pipelines = createPipelines(_connection, pipelineStage)
      sender ! IOBridge.Register(_connection)
      context.become(super.receive)
      commander ! ev

    case Status.Failure(CommandException(Connect(remoteAddress, _, _), msg, cause)) =>
      // if we cannot connect we die and let our supervisor handle the problem
      throw new ConnectException(remoteAddress, cause)
  }

  def createConnection(_key: IOBridge.Key, _tag: Any): Connection =
    new Connection {
      val key = _key
      val tag = _tag
      val handler = self
    }
}

object IOClientConnectionActor {

  // The default PipelineStage implements a very simple command/event frontend
  // that dispatches incoming events to the sender of the last command
  object DefaultPipelineStage extends PipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
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
  type Connect      = IOBridge.Connect;        val Connect = IOBridge.Connect
  type Close        = IOConnectionActor.Close; val Close   = IOConnectionActor.Close
  type Send         = IOConnectionActor.Send;  val Send    = IOConnectionActor.Send
  type Tell         = IOConnectionActor.Tell;  val Tell    = IOConnectionActor.Tell
  val StopReading   = IOConnectionActor.StopReading
  val ResumeReading = IOConnectionActor.ResumeReading

  ////////////// EVENTS //////////////
  type Connected  = IOBridge.Closed;              val Connected  = IOBridge.Connected
  type Closed     = IOConnectionActor.Closed;     val Closed     = IOConnectionActor.Closed
  type Received   = IOConnectionActor.Received;   val Received   = IOConnectionActor.Received
  type AckEvent   = IOConnectionActor.AckEvent;   val AckEvent   = IOConnectionActor.AckEvent
  type ActorDeath = IOConnectionActor.ActorDeath; val ActorDeath = IOConnectionActor.ActorDeath
}