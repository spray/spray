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

class IoClient(val ioWorker: IoWorker) extends IoPeer {
  import IoClient._

  override def preStart() {
    log.info("Starting {}", self.path)
  }

  override def postStop() {
    log.info("Stopped {}", self.path)
  }

  protected def receive = {
    case Connect(address) =>
      ioWorker ! IoWorker.Connect(self, address, sender)

    case IoWorker.Connected(key, receiver: ActorRef) =>
      val handle = createConnectionHandle(key)
      ioWorker ! IoWorker.Register(handle)
      receiver ! Connected(handle)

    case x: CommandError => log.warning("Received {}", x)
  }

}

trait IoClientApi extends IoPeerApi {

  ////////////// COMMANDS //////////////
  case class Connect(address: SocketAddress)
  object Connect {
    def apply(host: String, port: Int): Connect = Connect(new InetSocketAddress(host, port))
  }

  ////////////// EVENTS //////////////
  case class Connected(handle: Handle)

}

object IoClient extends IoClientApi