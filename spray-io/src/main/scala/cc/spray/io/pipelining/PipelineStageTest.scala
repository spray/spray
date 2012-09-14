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
import util.DynamicVariable
import java.nio.ByteBuffer
import java.net.InetSocketAddress
import akka.util.Duration
import akka.actor.{ActorRef, ActorContext, ActorSystem}
import akka.spray.UnregisteredActorRef
import cc.spray.io._
import cc.spray.util._


trait PipelineStageTest {
  implicit def system: ActorSystem

  lazy val testHandle = new Handle {
    def key = throw new UnsupportedOperationException
    def handler = throw new UnsupportedOperationException
    val remoteAddress = new InetSocketAddress("example.com", 8080)
    def localAddress = throw new UnsupportedOperationException
    def commander = throw new UnsupportedOperationException
    def tag = ()
  }

  lazy val sender1 = unregisteredActorRef
  lazy val sender2 = unregisteredActorRef
  lazy val sender3 = unregisteredActorRef
  lazy val sender4 = unregisteredActorRef

  def unregisteredActorRef = new UnregisteredActorRef(system) {
    protected def handle(message: Any, sender: ActorRef) {
      throw new UnsupportedOperationException
    }
  }

  class Fixture(stage: PipelineStage) {
    private var msgSender: ActorRef = null
    val context = new PipelineContext {
      def handle = testHandle
      def connectionActorContext = getConnectionActorContext
      override def sender = if (msgSender != null) msgSender else sys.error("No message sender set")
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
    def checkResult[T](body: => T): T = dynPR.withValue(this)(body)
  }

  private val dynPR = new DynamicVariable[PipelineRun](null)

  def result = {
    if (dynPR.value == null) sys.error("This value is only available inside of a `check` construct!")
    dynPR.value
  }
  def commands = result.commands.map {
    case IOPeer.Send(bufs, _) => SendStringCommand {
      val sb = new java.lang.StringBuilder
      for (b <- bufs) sb.append(b.copyContent.drainToString)
      sb.toString
    }
    case x => x
  }
  def events = result.events
  def command: Command = {
    val c = commands
    if (c.size == 1) c.head else sys.error("Expected a single command but got %s (%s)".format(c.size, c))
  }
  def event: Event = {
    val e = events
    if (e.size == 1) e.head else sys.error("Expected a single event but got %s (%s)".format(e.size, e))
  }

  case class Message(msg: AnyRef, sender: ActorRef) extends Command
  case class Do(f: PipelineRun => Unit) extends Command

  implicit def pimpAnyRefWithFrom(msg: AnyRef): { def from(s: ActorRef): Command } =
    new { def from(s: ActorRef) = Message(msg, s) }

  val ClearCommandAndEventCollectors = Do(_.clear())
  def Received(rawMessage: String) = IOBridge.Received(testHandle, string2ByteBuffer(rawMessage))
  def Send(rawMessage: String) = IOPeer.Send(string2ByteBuffer(rawMessage))
  def SendString(rawMessage: String) = SendStringCommand(rawMessage)
  def Sleep(duration: String) = Do(_ => Thread.sleep(Duration(duration).toMillis))

  case class SendStringCommand(string: String) extends Command

  protected def string2ByteBuffer(s: String) = ByteBuffer.wrap(s.getBytes("US-ASCII"))

  def fixTells(commands: Seq[Command]) = commands.map {
    case x: IOPeer.Tell => x.copy(sender = IgnoreSender)
    case x => x
  }

  def IgnoreSender: ActorRef = null
}
