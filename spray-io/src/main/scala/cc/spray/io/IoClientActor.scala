/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

import akka.actor.ActorRef
import java.net.{InetSocketAddress, SocketAddress}

class IoClientActor(val ioWorker: ActorRef) extends IoPeerActor {

  override def preStart() {
    log.info("Starting {}", self.path)
  }

  override def postStop() {
    log.info("Stopped {}", self.path)
  }

  protected def receive = {
    case ClientConnect(address) =>
      ioWorker ! Connect(self, address, sender)

    case Connected(key, receiver: ActorRef) =>
      val handle = createConnectionHandle(key)
      ioWorker ! Register(handle)
      receiver ! handle

    case x: CommandError =>
      log.warning("Received {}", x)
  }

}

case class ClientConnect(address: SocketAddress)
object ClientConnect {
  def apply(host: String, port: Int): ClientConnect =
    ClientConnect(new InetSocketAddress(host, port))
}