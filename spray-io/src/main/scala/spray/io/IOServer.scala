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

import java.net.InetSocketAddress
import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import spray.util.SprayActorLogging


trait IOServer extends Actor with SprayActorLogging {
  import IOServer._
  private[this] val debug = TaggableLog(log, Logging.DebugLevel)
  private[this] val info = TaggableLog(log, Logging.InfoLevel)

  def receive = unbound

  def unbound: Receive = {
    case cmd@ Bind(endpoint, _, tag) =>
      debug.log(tag, "Starting {} on {}", self.path, endpoint)
      context.become(binding(endpoint, sender))
      IOExtension(context.system).ioBridge() ! cmd
  }

  def binding(endpoint: InetSocketAddress, commander: ActorRef): Receive = {
    case IOBridge.Bound(key, tag) =>
      context.become(bound(endpoint, key, tag))
      info.log(tag, "{} started on {}", self.path, endpoint)
      commander ! Bound(endpoint, tag)
  }

  def bound(endpoint: InetSocketAddress, bindingKey: IOBridge.Key, bindingTag: Any): Receive = {
    case IOBridge.Connected(key, tag) =>
      sender ! IOBridge.Register(createConnection(key, tag))

    case Unbind =>
      debug.log(bindingTag, "Stopping {} on {}", self.path, endpoint)
      context.become(unbinding(endpoint, sender))
      IOExtension(context.system).ioBridge() ! IOBridge.Unbind(bindingKey)
  }

  def unbinding(endpoint: InetSocketAddress, commander: ActorRef): Receive = {
    case IOBridge.Unbound(_, tag) =>
      info.log(tag, "{} stopped on {}", self.path, endpoint)
      context.become(unbound)
      commander ! Unbound(endpoint, tag)
  }

  // default implementation, overridden for example by the ConnectionActors mix in
  def createConnection(_key: IOBridge.Key, _tag: Any): Connection =
    new Connection {
      val key = _key
      val tag = _tag
      def handler = self
    }
}

object IOServer {

  ////////////// COMMANDS //////////////
  type Bind = IOBridge.Bind; val Bind = IOBridge.Bind
  case object Unbind extends Command

  ////////////// EVENTS //////////////
  case class Bound(endpoint: InetSocketAddress, tag: Any)
  case class Unbound(endpoint: InetSocketAddress, tag: Any)
}