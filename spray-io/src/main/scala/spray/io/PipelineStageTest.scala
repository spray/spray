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

import collection.mutable.ListBuffer
import util.DynamicVariable
import java.nio.ByteBuffer
import java.net.InetSocketAddress
import akka.actor.{ActorRef, ActorContext, ActorSystem}
import akka.spray.UnregisteredActorRef
import spray.io._
import spray.util._
import annotation.tailrec


trait PipelineStageTest { test =>
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
    def handle(message: Any)(implicit sender: ActorRef) {
      throw new UnsupportedOperationException
    }
  }

  def connectionActorContext: ActorContext = throw new UnsupportedOperationException

  implicit def pimpPipelineStageWithTest(stage: PipelineStage): { def test[T](body: => T): T } =
    new { def test[T](body: => T): T = new Fixture(stage).run(body) }

  private class Fixture(stage: PipelineStage) {
    var msgSender: ActorRef = null
    val commands = ListBuffer.empty[Command]
    val events = ListBuffer.empty[Event]
    val pipelines = {
      val context = new PipelineContext {
        def handle = testHandle
        def connectionActorContext = test.connectionActorContext
        override def sender = if (msgSender != null) msgSender else sys.error("No message sender set")
      }
      stage.build(context, x => commands += x, x => events += x)
    }
    def clear() { commands.clear(); events.clear() }
    def run[T](body: => T): T = dynFixture.withValue(this)(body)
  }

  private val dynFixture = new DynamicVariable[Fixture](null)

  private def fixture = {
    if (dynFixture.value == null) sys.error("This value is only available inside of a `test` construct!")
    dynFixture.value
  }

  case class ProcessResult(commands: List[Command], events: List[Event]) // source-quote-result

  def currentTestPipelines: Pipelines = fixture.pipelines

  def result = ProcessResult(
    extractCommands(fixture.commands.toList),
    extractEvents(fixture.events.toList)
  )

  def clear() { fixture.clear() }

  //# source-quote-clears
  def clearAndProcess(cmdsAndEvents: AnyRef*): ProcessResult = {
    clear()
    process(cmdsAndEvents.toList)
  }

  def processAndClear(cmdsAndEvents: AnyRef*): ProcessResult = {
    val x = process(cmdsAndEvents.toList)
    clear()
    x
  }
  //#

  def process(cmdsAndEvents: AnyRef*): ProcessResult = // source-quote-process
    process(cmdsAndEvents.toList)

  @tailrec
  final def process(cmdsAndEvents: List[AnyRef]): ProcessResult = cmdsAndEvents match {
    case Nil                        => result
    case Message(msg, sndr) :: rest => fixture.msgSender = sndr; process(msg :: rest)
    case (x: Command) :: rest       => fixture.pipelines.commandPipeline(x); process(rest)
    case (x: Event) :: rest         => fixture.pipelines.eventPipeline(x); process(rest)
  }

  def extractCommands(commands: List[Command]): List[Command] = commands.map {
    case IOPeer.Send(bufs, _) => SendString {
      val sb = new java.lang.StringBuilder
      for (b <- bufs) sb.append(b.duplicate.drainToString)
      sb.toString
    }
    case x => x
  }

  def extractEvents(events: List[Event]): List[Event] = events.map {
    case IOPeer.Received(_, buffer) => ReceivedString(buffer.duplicate.drainToString)
    case x => x
  }

  object Commands {
    def unapplySeq(pr: ProcessResult): Option[Seq[Command]] = Some(pr.commands)
  }
  object Events {
    def unapplySeq(pr: ProcessResult): Option[Seq[Event]] = Some(pr.events)
  }
  object ActorPathName {
    def unapply(ref: ActorRef): Option[String] = Some(ref.path.name)
  }

  case class Message(msg: AnyRef, sender: ActorRef) // source-quote-message

  implicit def pimpAnyRefWithFrom(msg: AnyRef) = new PimpedAnyRef(msg)
  class PimpedAnyRef(msg: AnyRef) {
    def from(sender: ActorRef) = Message(msg, sender)
  }

  def Send(rawMessage: String) = IOPeer.Send(string2ByteBuffer(rawMessage))
  def Received(rawMessage: String) = IOBridge.Received(testHandle, string2ByteBuffer(rawMessage))

  case class SendString(string: String) extends Command
  case class ReceivedString(string: String) extends Event

  protected def string2ByteBuffer(s: String) = ByteBuffer.wrap(s.getBytes("US-ASCII"))
}
