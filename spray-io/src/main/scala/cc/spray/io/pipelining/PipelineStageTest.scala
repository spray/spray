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

package cc.spray.io.pipelining

import collection.mutable.ListBuffer
import cc.spray.io._
import java.nio.ByteBuffer
import akka.util.Duration
import java.net.InetSocketAddress
import akka.actor.{ActorRef, ActorContext, ActorSystem}


trait PipelineStageTest {
  implicit val system = ActorSystem()

  val dummyHandle = new Handle {
    def key = throw new UnsupportedOperationException
    def handler = throw new UnsupportedOperationException
    val remoteAddress = new InetSocketAddress("example.com", 8080)
    def commander = throw new UnsupportedOperationException
  }

  class Fixture(stage: PipelineStage) {
    private var msgSender = system.deadLetters
    val context = new PipelineContext {
      def handle = dummyHandle
      def connectionActorContext = getConnectionActorContext
      override def sender = msgSender
    }
    def getConnectionActorContext: ActorContext = throw new UnsupportedOperationException
    def apply(cmdsAndEvents: AnyRef*) = process(new PipelineRun(stage, context), cmdsAndEvents.toList)
    def process(run: PipelineRun, cmdsAndEvents: List[AnyRef]): PipelineRun = cmdsAndEvents match {
      case Nil                        => run
      case Do(f) :: rest              => f(run); process(run, rest)
      case Message(msg, sndr) :: rest => msgSender = sndr; process(run, msg :: rest)
      case (x: Command) :: rest       => run.pipelines.commandPipeline(x); process(run, rest)
      case (x: Event) :: rest         => run.pipelines.eventPipeline(x); process(run, rest)
    }
  }

  class PipelineRun(stage: PipelineStage, context: PipelineContext) {
    private val _commands = ListBuffer.empty[Command]
    private val _events = ListBuffer.empty[Event]
    def commands: Seq[Command] = _commands
    def events: Seq[Event] = _events
    def clear() { _commands.clear(); _events.clear() }
    val pipelines = stage.buildPipelines(context, x => _commands += x, x => _events += x)
  }

  case class Message(msg: AnyRef, sender: ActorRef) extends Command
  case class Do(f: PipelineRun => Unit) extends Command

  val ClearCommandAndEventCollectors = Do(_.clear())

  def Received(rawMessage: String) = IoWorker.Received(dummyHandle, string2ByteBuffer(rawMessage))
  def Send(rawMessage: String) = IoPeer.Send(string2ByteBuffer(rawMessage))
  def SendString(rawMessage: String) = SendStringCommand(rawMessage)
  def Sleep(duration: String) = Do(_ => Thread.sleep(Duration(duration).toMillis))

  case class SendStringCommand(string: String) extends Command

  protected def string2ByteBuffer(s: String) = ByteBuffer.wrap(s.getBytes("US-ASCII"))

  def cleanup() {
    system.shutdown()
  }

  def fixSends(commands: Seq[Command]) = commands.map {
    case IoPeer.Send(bufs, _) => SendStringCommand {
      val sb = new java.lang.StringBuilder
      for (b <- bufs) while (b.remaining > 0) sb.append(b.get.toChar)
      sb.toString
    }
    case x => x
  }

  def fixTells(commands: Seq[Command]) = commands.map {
    case x: IoPeer.Tell => x.copy(sender = IgnoreSender)
    case x => x
  }

  def IgnoreSender: ActorRef = null
}
