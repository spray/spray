/*
 * Copyright (C) 2011-2012 spray.cc
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
      val handler = context.actorOf(Props(createConnectionActor(this)), "connection-"+localAddress.getHostName+":"+localAddress.getPort+"--"+remoteAddress.getHostName+":"+remoteAddress.getPort) // must be initialized last
    }
  }

  protected def createConnectionActor(handle: Handle): IOConnectionActor = new IOConnectionActor(handle)

  protected def pipeline: PipelineStage

  class IOConnectionActor(val handle: Handle) extends Actor {
    val pipelines = pipeline.buildPipelines(
      context = createPipelineContext,
      commandPL = baseCommandPipeline,
      eventPL = baseEventPipeline
    )

    def createPipelineContext: PipelineContext = PipelineContext(handle, context)

    def trace(msg: String, args: Any*) {
      if (false) args match {
        case Seq() => log.info(msg)
        case Seq(a) => log.info(msg, a)
        case Seq(a, b) => log.info(msg, a, b)
        case Seq(a, b, c) => log.info(msg, a, b, c)
      }

    }

    //# final-stages
    def baseCommandPipeline: Pipeline[Command] = {
      case IOPeer.Send(buffers, ack)          =>
        trace("<- Send({})", buffers.map(_.remaining).sum)
        ioBridge ! IOBridge.Send(handle, buffers, ack)
      case IOPeer.Close(reason)               =>
        trace("<- Close({})", reason)
        ioBridge ! IOBridge.Close(handle, reason)
      case IOPeer.StopReading                 =>
        trace("<- StopReading")
        ioBridge ! IOBridge.StopReading(handle)
      case IOPeer.ResumeReading               =>
        trace("<- ResumeReadingReading")
        ioBridge ! IOBridge.ResumeReading(handle)
      case IOPeer.Tell(receiver, msg, sender) =>
        trace("<- Tell({} -> {}, {})", sender, receiver, msg.getClass)
        receiver.tell(msg, sender)
      case x: Droppable => // don't warn
        trace("Dropped Command: {}", x)
      case cmd =>
        //Thread.dumpStack()
        log.warning("commandPipeline: dropped {}", cmd)
    }

    def baseEventPipeline: Pipeline[Event] = {
      case x: IOPeer.Closed =>
        log.debug("Stopping connection actor, connection was closed due to {}", x.reason)
        context.stop(self)
        context.parent ! x // also inform our parent of our closing
      case x: Droppable => // don't warn
        trace("Dropped Event: {}", x)
      case ev =>
        //Thread.dumpStack()
        log.warning("eventPipeline: dropped {}", ev)
    }
    //#

    //# receive
    def receive = {
      case x: Command =>
        trace("-> Command: {}", x)
        pipelines.commandPipeline(x)
      case x: Event =>
        trace("-> Event: {}", x)
        pipelines.eventPipeline(x)
      case Status.Failure(x: CommandException) =>
        trace("-> Failure: {}", x)
        pipelines.eventPipeline(x)
      case Terminated(actor) =>
        trace("-> Terminated: {}", actor)
        pipelines.eventPipeline(IOPeer.ActorDeath(actor))
    }
    //#
  }

}