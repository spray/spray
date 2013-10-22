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

import java.net.InetSocketAddress
import akka.actor.{ ActorLogging, ActorRef, Terminated, Actor }
import akka.io.Tcp

trait ConnectionHandler extends Actor with ActorLogging {

  //# final-stages
  def baseCommandPipeline(tcpConnection: ActorRef): Pipeline[Command] = {
    case x @ (_: Tcp.WriteCommand | _: Tcp.CloseCommand) ⇒ tcpConnection ! x
    case Pipeline.Tell(receiver, msg, sender) ⇒ receiver.tell(msg, sender)
    case x @ (Tcp.SuspendReading | Tcp.ResumeReading | Tcp.ResumeWriting) ⇒ tcpConnection ! x
    case _: Droppable ⇒ // don't warn
    case cmd ⇒ log.warning("command pipeline: dropped {}", cmd)
  }

  def baseEventPipeline: Pipeline[Event] = {
    case x: Tcp.ConnectionClosed ⇒
      log.debug("Stopping connection actor, connection was {}", x)
      context.stop(self)

    case _: Droppable ⇒ // don't warn
    case ev           ⇒ log.warning("event pipeline: dropped {}", ev)
  }
  //#

  def running(tcpConnection: ActorRef, stage: PipelineStage, remoteAddress: InetSocketAddress,
              localAddress: InetSocketAddress): Receive = {
    val pipelineContext = PipelineContext(context, remoteAddress, localAddress, log)
    running(tcpConnection, stage, pipelineContext)
  }

  def running[C <: PipelineContext](tcpConnection: ActorRef, pipelineStage: RawPipelineStage[C],
                                    pipelineContext: C): Receive = {
    val stage = pipelineStage(pipelineContext, baseCommandPipeline(tcpConnection), baseEventPipeline)
    running(tcpConnection, stage)
  }

  def running(tcpConnection: ActorRef, pipelines: Pipelines): Receive = {
    case x: Command                  ⇒ pipelines.commandPipeline(x)
    case x: Event                    ⇒ pipelines.eventPipeline(x)
    case Terminated(`tcpConnection`) ⇒ pipelines.eventPipeline(Tcp.ErrorClosed("TcpConnection actor died"))
    case Terminated(actor)           ⇒ pipelines.eventPipeline(Pipeline.ActorDeath(actor))
  }
}

class SimpleConnectionHandler(tcpConnection: ActorRef,
                              pipelineStage: PipelineStage,
                              remoteAddress: InetSocketAddress,
                              localAddress: InetSocketAddress) extends ConnectionHandler {
  def receive = running(tcpConnection, pipelineStage, remoteAddress, localAddress)
}
