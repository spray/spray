/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.can

import akka.config.Config._
import java.net.InetSocketAddress

trait CanConfig {
  def endpoint: InetSocketAddress
  def dispatchActorId: String
  def readBufferSize: Int
}

object AkkaConfConfig extends CanConfig {
  lazy val hostname        = config.getString("spray.can.hostname", "localhost")
  lazy val port            = config.getInt("spray.can.port", 8888)
  lazy val dispatchActorId = config.getString("spray.can.dispatch-actor-id", "spray-root-service")
  lazy val readBufferSize  = config.getInt("spray.can.read-buffer-size", 8192)
  def endpoint             = new InetSocketAddress(hostname, port)

  override def toString =
    "AkkaConfConfig(\n" +
    "  hostname       : " + hostname + "\n" +
    "  port           : " + port + "\n" +
    "  dispatchActorId: " + dispatchActorId + "\n" +
    "  readBufferSize : " + readBufferSize + "\n" +
    ")\n"
}

case class SimpleConfig(
  hostname: String = "localhost",
  port: Int = 8888,
  dispatchActorId: String = "spray-root-service",
  readBufferSize: Int = 8192
) extends CanConfig {
  def endpoint = new InetSocketAddress(hostname, port)
}