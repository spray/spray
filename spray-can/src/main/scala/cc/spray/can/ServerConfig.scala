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

case class ServerConfig(
  // ServerConfig
  hostname: String = "localhost",
  port: Int = 8080,
  serverActorId: String = "spray-can-server",
  serviceActorId: String = "spray-root-service",
  timeoutActorId: String = "spray-root-service",
  timeoutTimeout: Long = 500,
  serverHeader: String = "spray-can/" + SprayCanVersion,

  // PeerConfig
  readBufferSize: Int = 8192,
  idleTimeout: Long = 10000,
  reapingCycle: Long = 500,
  requestTimeout: Long = 5000,
  timeoutCycle: Long = 200
) extends PeerConfig {

  require(!serverActorId.isEmpty, "serverActorId must not be empty")
  require(!serviceActorId.isEmpty, "serviceActorId must not be empty")
  require(!timeoutActorId.isEmpty, "timeoutActorId must not be empty")
  require(timeoutTimeout >= 0, "timeoutTimeout must be >= 0 ms")

  def endpoint = new InetSocketAddress(hostname, port)

  override def toString =
    "ServerConfig(\n" +
    "  endpoint       : " + endpoint + "\n" +
    "  serverActorId  : " + serverActorId + "\n" +
    "  serviceActorId : " + serviceActorId + "\n" +
    "  timeoutActorId : " + timeoutActorId + "\n" +
    "  timeoutTimeout : " + timeoutTimeout + " ms\n" +
    "  serverHeader   : " + serverHeader + "\n" +
    "  readBufferSize : " + readBufferSize + " bytes\n" +
    "  idleTimeout    : " + idleTimeout + " ms\n" +
    "  reapingCycle   : " + reapingCycle + " ms\n" +
    "  requestTimeout : " + requestTimeout + " ms\n" +
    "  timeoutCycle   : " + timeoutCycle + " ms\n"
    ")"
}

object ServerConfig {
  lazy val fromAkkaConf = ServerConfig(
    // ServerConfig
    hostname       = config.getString("spray-can.server.hostname", "localhost"),
    port           = config.getInt("spray-can.server.port", 8080),
    serverActorId  = config.getString("spray-can.server.server-actor-id", "spray-can-server"),
    serviceActorId = config.getString("spray-can.server.service-actor-id", "spray-root-service"),
    timeoutActorId = config.getString("spray-can.server.timeout-actor-id", "spray-root-service"),
    timeoutTimeout = config.getLong("spray-can.server.timeout-timeout", 500),
    serverHeader   = config.getString("spray-can.server.server-header", "spray-can/" + SprayCanVersion),

    // PeerConfig
    readBufferSize = config.getInt("spray-can.server.read-buffer-size", 8192),
    idleTimeout    = config.getLong("spray-can.server.idle-timeout", 10000),
    reapingCycle   = config.getLong("spray-can.server.reaping-cycle", 500),
    requestTimeout = config.getLong("spray-can.server.request-timeout", 5000),
    timeoutCycle   = config.getLong("spray-can.server.timeout-cycle", 200)
  )
}

case class ClientConfig(
  // ClientConfig
  clientActorId: String = "spray-can-client",
  userAgentHeader: String = "spray-can/" + SprayCanVersion,

  // PeerConfig
  readBufferSize: Int = 8192,
  idleTimeout: Long = 10000,
  reapingCycle: Long = 500,
  requestTimeout: Long = 5000,
  timeoutCycle: Long = 200
) extends PeerConfig {

  require(!clientActorId.isEmpty, "serverActorId must not be empty")

  override def toString =
    "ClientConfig(\n" +
    "  clientActorId  : " + clientActorId + "\n" +
    "  userAgentHeader: " + userAgentHeader + "\n" +
    "  readBufferSize : " + readBufferSize + " bytes\n" +
    "  idleTimeout    : " + idleTimeout + " ms\n" +
    "  reapingCycle   : " + reapingCycle + " ms\n" +
    "  requestTimeout : " + requestTimeout + " ms\n" +
    "  timeoutCycle   : " + timeoutCycle + " ms\n"
    ")"
}

object ClientConfig {
  lazy val fromAkkaConf = ClientConfig(
    // ClientConfig
    clientActorId   = config.getString("spray-can.client.client-actor-id", "spray-can-client"),
    userAgentHeader = config.getString("spray-can.client.user-agent-header", "spray-can/" + SprayCanVersion),

    // PeerConfig
    readBufferSize = config.getInt("spray-can.server.read-buffer-size", 8192),
    idleTimeout    = config.getLong("spray-can.server.idle-timeout", 10000),
    reapingCycle   = config.getLong("spray-can.server.reaping-cycle", 500),
    requestTimeout = config.getLong("spray-can.server.request-timeout", 5000),
    timeoutCycle   = config.getLong("spray-can.server.timeout-cycle", 200)
  )
}

