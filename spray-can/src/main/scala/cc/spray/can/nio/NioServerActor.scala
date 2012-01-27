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

package cc.spray.can.nio

import akka.actor.Actor
import cc.spray.can.config.NioServerConfig
import java.net.InetSocketAddress
import cc.spray.can.util.Logging

abstract class NioServerActor(val config: NioServerConfig, val nioWorker: NioWorker) extends Actor with Logging {

  private val endpoint = new InetSocketAddress(config.host, config.port)
  private var bindingKey: Option[Key] = None

  override def preStart() {
    log.info("Starting {} on {}", config.label, endpoint)
    nioWorker.start() // start if not started yet
    nioWorker ! Bind(
      handleCreator = self,
      address = endpoint,
      backlog = config.bindingBacklog,
      ackTo = Some(self)
    )
  }

  override def postStop() {
    for (key <- bindingKey) {
      log.info("Stopping {} on {}", config.label, endpoint)
      nioWorker ! Unbind(key)
    }
  }

  protected def receive = {
    case Bound(key) =>
      bindingKey = Some(key)
      log.info("{} started on {}", config.label, endpoint)

    case Connected(key) =>
      nioWorker ! Register(createConnectionHandle(key))

    case x: CommandError =>
      log.warn("Received {}", x)
  }

  protected def createConnectionHandle(key: Key): Handle
}