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

package akka.spray.io

import java.nio.channels.spi.SelectorProvider
import scala.annotation.tailrec
import spray.io.{IOBridge, Command}
import akka.actor._
import akka.dispatch._


class SelectorWakingMailbox(system: ActorSystem, _messageQueue: MessageQueue) extends Mailbox(_messageQueue) {
  val selector = SelectorProvider.provider.openSelector

  override def enqueue(receiver: ActorRef, handle: Envelope) {
    super.enqueue(receiver, handle)
    selector.wakeup()
    handle.message match {
      case _: Command => // commands are handled by the IOBridge.receive, so all is well
      case _: Kill | _: PoisonPill => // nothing to do since the bridge will die anyway
      case _ =>
        // all other messages (e.g. AutoReceivedMessages) do not reach the IOBridges `receive` method
        // and thus stop the selection loop, therefore we need to explicitly restart it with a dedicated message
        enqueueSelect(receiver)
    }
  }

  def systemEnqueue(receiver: ActorRef, message: SystemMessage) {
    doSystemEnqueue(receiver, message)
    selector.wakeup()
    enqueueSelect(receiver)
  }

  private def enqueueSelect(receiver: ActorRef) {
    super.enqueue(receiver, Envelope(IOBridge.Select, null, system))
  }

  def isEmpty = !hasMessages && !hasSystemMessages

  //---------- copied almost verbatim from DefaultSystemMessageQueue trait ------------
  // we need to inject a selector wakeup into the systemEnqueue method, which is
  // final in DefaultSystemMessageQueue (for the @tailrec)
  // TODO proper solution: "unfinalize" in DefaultSystemMessageQueue, mix in and override here

  @tailrec
  private def doSystemEnqueue(receiver: ActorRef, message: SystemMessage): Unit = {
    assert(message.next eq null)
    if (Mailbox.debug) println(receiver + " having enqueued " + message)
    val head = systemQueueGet
    if (head == NoMessage) {
      if (actor ne null) actor.systemImpl.deadLetterMailbox.systemEnqueue(receiver, message)
    } else {
      /*
       * this write is safely published by the compareAndSet contained within
       * systemQueuePut; “Intra-Thread Semantics” on page 12 of the JSR133 spec
       * guarantees that “head” uses the value obtained from systemQueueGet above.
       * Hence, SystemMessage.next does not need to be volatile.
       */
      message.next = head
      if (!systemQueuePut(head, message)) {
        message.next = null
        doSystemEnqueue(receiver, message)
      }
    }
  }

  @tailrec
  final def systemDrain(newContents: SystemMessage): SystemMessage = {
    val head = systemQueueGet
    if (systemQueuePut(head, newContents)) SystemMessage.reverse(head) else systemDrain(newContents)
  }

  def hasSystemMessages: Boolean = systemQueueGet ne null

  // -------------- end of DefaultSystemMessageQueue copy --------------------
}

