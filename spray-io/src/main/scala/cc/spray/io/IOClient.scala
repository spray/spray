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

import spray.util.Reply
import akka.actor.{Status, ActorRef}

abstract class IOClient(val ioBridge: IOBridge) extends IOPeer {
  import IOClient._

  override def preStart() {
    log.info("Starting {}", self.path)
  }

  override def postStop() {
    log.info("Stopped {}", self.path)
  }

  protected def receive = {
    case cmd: Connect =>
      ioBridge.tell(cmd, Reply.withContext(sender))

    case Reply(IOBridge.Connected(key, remoteAddress, localAddress, tag), commander: ActorRef) =>
      val handle = createConnectionHandle(key, remoteAddress, localAddress, commander, tag)
      ioBridge ! IOBridge.Register(handle)
      commander ! Connected(handle)

    case x: Closed =>
      // inform the original connection commander of the closing
      x.handle.commander ! x

    case Reply(Status.Failure(CommandException(Connect(remoteAddress, _, _), msg, cause)), commander: ActorRef) =>
      commander ! Status.Failure(IOClientException("Couldn't connect to " + remoteAddress, cause))
  }
}

object IOClient {

  case class IOClientException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

  ////////////// COMMANDS //////////////
  type Connect  = IOBridge.Connect; val Connect = IOBridge.Connect
  type Close    = IOPeer.Close;     val Close = IOPeer.Close
  type Send     = IOPeer.Send;      val Send = IOPeer.Send
  type Tell     = IOPeer.Tell;      val Tell = IOPeer.Tell // only available with ConnectionActors mixin
  val StopReading = IOPeer.StopReading
  val ResumeReading = IOPeer.ResumeReading

  ////////////// EVENTS //////////////
  case class Connected(handle: Handle) extends Event
  type Closed = IOPeer.Closed;     val Closed = IOPeer.Closed
  type AckEvent = IOPeer.AckEvent; val AckEvent = IOPeer.AckEvent
  type Received = IOPeer.Received; val Received = IOPeer.Received
}