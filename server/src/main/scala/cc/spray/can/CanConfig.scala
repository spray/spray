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
  def serverActorId: String
  def timeoutKeeperActorId: String
  def serviceActorId: String
  def timeoutServiceActorId: String
  def readBufferSize: Int
  def idleTimeout: Long
  def reapingCycle: Long
  def requestTimeout: Long
  def timeoutTimeout: Long
  def timeoutCycle: Long

  require(!serverActorId.isEmpty, "serverActorId must not be empty")
  require(!timeoutKeeperActorId.isEmpty, "timeoutKeeperActorId must not be empty")
  require(!serviceActorId.isEmpty, "serviceActorId must not be empty")
  require(!timeoutServiceActorId.isEmpty, "timeoutServiceActorId must not be empty")
  require(readBufferSize > 0, "readBufferSize must be > 0 bytes")
  require(idleTimeout > 0, "idleTimeout must be > 0 ms")
  require(reapingCycle > 0, "reapingCycle must be > 0 ms")
  require(requestTimeout >= 0, "requestTimeout must be >= 0 ms")
  require(timeoutTimeout >= 0, "timeoutTimeout must be >= 0 ms")
  require(timeoutCycle > 0, "timeoutCycle must be > 0 ms")
}

object AkkaConfConfig extends CanConfig {
  lazy val hostname              = config.getString("spray.can.hostname", "localhost")
  lazy val port                  = config.getInt("spray.can.port", 8888)
  lazy val serverActorId         = config.getString("spray.can.server-actor-id", "spray-can-server")
  lazy val timeoutKeeperActorId  = config.getString("spray.can.timeout-keeper-actor-id", "spray-can-timeout-keeper")
  lazy val serviceActorId        = config.getString("spray.can.service-actor-id", "spray-root-service")
  lazy val timeoutServiceActorId = config.getString("spray.can.service-actor-id", "spray-root-service")
  lazy val readBufferSize        = config.getInt("spray.can.read-buffer-size", 8192)
  lazy val idleTimeout           = config.getLong("spray.can.idle-timeout", 10000)
  lazy val reapingCycle          = config.getLong("spray.can.reaping-cycle", 500)
  lazy val requestTimeout        = config.getLong("spray.can.request-timeout", 5000)
  lazy val timeoutTimeout        = config.getLong("spray.can.timeout-timeout", 500)
  lazy val timeoutCycle          = config.getLong("spray.can.timeout-cycle", 200)
  def endpoint = new InetSocketAddress(hostname, port)

  override def toString =
    "AkkaConfConfig(\n" +
    "  hostname              : " + hostname + "\n" +
    "  port                  : " + port + "\n" +
    "  serverActorId         : " + serverActorId + "\n" +
    "  timeoutKeeperActorId  : " + timeoutKeeperActorId + "\n" +
    "  serviceActorId        : " + serviceActorId + "\n" +
    "  timeoutServiceActorId : " + timeoutServiceActorId + "\n" +
    "  readBufferSize        : " + readBufferSize + " bytes\n" +
    "  idleTimeout           : " + idleTimeout + " ms\n" +
    "  reapingCycle          : " + reapingCycle + " ms\n" +
    "  requestTimeout        : " + requestTimeout + " ms\n" +
    "  timeoutTimeout        : " + timeoutTimeout + " ms\n" +
    "  timeoutCycle          : " + timeoutCycle + " ms\n"
    ")"
}

case class SimpleConfig(
  hostname: String = "localhost",
  port: Int = 8888,
  serverActorId: String = "spray-can-server",
  timeoutKeeperActorId: String = "spray-can-timeout-keeper",
  serviceActorId: String = "spray-root-service",
  timeoutServiceActorId: String = "spray-root-service",
  readBufferSize: Int = 8192,
  idleTimeout: Long = 10000,
  reapingCycle: Long = 500,
  requestTimeout: Long = 5000,
  timeoutTimeout: Long = 500,
  timeoutCycle: Long = 200
) extends CanConfig {
  def endpoint = new InetSocketAddress(hostname, port)
}