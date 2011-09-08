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

trait PeerConfig {
  def readBufferSize: Int
  def idleTimeout: Long
  def reapingCycle: Long
  def requestTimeout: Long
  def timeoutCycle: Long

  require(readBufferSize > 0, "readBufferSize must be > 0 bytes")
  require(idleTimeout >= 0, "idleTimeout must be >= 0 ms")
  require(reapingCycle > 0, "reapingCycle must be > 0 ms")
  require(requestTimeout >= 0, "requestTimeout must be >= 0 ms")
  require(timeoutCycle > 0, "timeoutCycle must be > 0 ms")
}

trait ServerConfig extends PeerConfig {
  def endpoint: InetSocketAddress
  def serverActorId: String
  def serviceActorId: String
  def timeoutServiceActorId: String
  def timeoutTimeout: Long
  def serverHeader: String

  require(!serverActorId.isEmpty, "serverActorId must not be empty")
  require(!serviceActorId.isEmpty, "serviceActorId must not be empty")
  require(!timeoutServiceActorId.isEmpty, "timeoutServiceActorId must not be empty")
  require(timeoutTimeout >= 0, "timeoutTimeout must be >= 0 ms")

  override def toString =
    getClass.getSimpleName + "(\n" +
    "  endpoint              : " + endpoint + "\n" +
    "  serverActorId         : " + serverActorId + "\n" +
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

trait ClientConfig extends PeerConfig {
  def clientActorId: String
  def userAgentHeader: String

  require(!clientActorId.isEmpty, "serverActorId must not be empty")
}

object AkkaConfServerConfig extends ServerConfig {
  // PeerConfig
  lazy val readBufferSize        = config.getInt("spray.can-server.read-buffer-size", 8192)
  lazy val idleTimeout           = config.getLong("spray.can-server.idle-timeout", 10000)
  lazy val reapingCycle          = config.getLong("spray.can-server.reaping-cycle", 500)
  lazy val requestTimeout        = config.getLong("spray.can-server.request-timeout", 5000)
  lazy val timeoutCycle          = config.getLong("spray.can-server.timeout-cycle", 200)

  // ServerConfig
  lazy val hostname              = config.getString("spray.can-server.hostname", "localhost")
  lazy val port                  = config.getInt("spray.can-server.port", 8080)
  lazy val serverActorId         = config.getString("spray.can-server.server-actor-id", "spray-can-server")
  lazy val serviceActorId        = config.getString("spray.can-server.service-actor-id", "spray-root-service")
  lazy val timeoutServiceActorId = config.getString("spray.can-server.timeout-service-actor-id", "spray-root-service")
  lazy val timeoutTimeout        = config.getLong("spray.can-server.timeout-timeout", 500)
  lazy val serverHeader          = config.getString("spray.can-server.server-header", "spray-can/" + SprayCanVersion)
  def endpoint = new InetSocketAddress(hostname, port)
}

object AkkaConfClientConfig extends ClientConfig {
  // PeerConfig
  lazy val readBufferSize        = config.getInt("spray.can-client.read-buffer-size", 8192)
  lazy val idleTimeout           = config.getLong("spray.can-client.idle-timeout", 10000)
  lazy val reapingCycle          = config.getLong("spray.can-client.reaping-cycle", 500)
  lazy val requestTimeout        = config.getLong("spray.can-client.request-timeout", 5000)
  lazy val timeoutCycle          = config.getLong("spray.can-client.timeout-cycle", 200)

  // ClientConfig
  lazy val clientActorId         = config.getString("spray.can-client.client-actor-id", "spray-can-client")
  lazy val userAgentHeader       = config.getString("spray.can-client.user-agent-header", "spray-can/" + SprayCanVersion)
}

case class SimpleServerConfig(
  // ServerConfig
  hostname: String = "localhost",
  port: Int = 8080,
  serverActorId: String = "spray-can-server",
  timeoutKeeperActorId: String = "spray-can-timeout-keeper",
  serviceActorId: String = "spray-root-service",
  timeoutServiceActorId: String = "spray-root-service",
  timeoutTimeout: Long = 500,
  serverHeader: String = "spray-can/" + SprayCanVersion,

  // PeerConfig
  readBufferSize: Int = 8192,
  idleTimeout: Long = 10000,
  reapingCycle: Long = 500,
  requestTimeout: Long = 5000,
  timeoutCycle: Long = 200
) extends ServerConfig {
  def endpoint = new InetSocketAddress(hostname, port)
}

case class SimpleClientConfig(
  // ClientConfig
  clientActorId: String = "spray-can-client",
  userAgentHeader: String = "spray-can/" + SprayCanVersion,

  // PeerConfig
  readBufferSize: Int = 8192,
  idleTimeout: Long = 10000,
  reapingCycle: Long = 500,
  requestTimeout: Long = 5000,
  timeoutCycle: Long = 200
) extends ClientConfig