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
import akka.actor.Actor

trait PeerConfig {
  def readBufferSize: Int
  def idleTimeout: Long
  def reapingCycle: Long
  def requestTimeout: Long
  def timeoutCycle: Long
  def parserConfig: MessageParserConfig

  require(readBufferSize > 0, "readBufferSize must be > 0 bytes")
  require(idleTimeout >= 0, "idleTimeout must be >= 0 ms")
  require(reapingCycle > 0, "reapingCycle must be > 0 ms")
  require(requestTimeout >= 0, "requestTimeout must be >= 0 ms")
  require(timeoutCycle > 0, "timeoutCycle must be > 0 ms")
  require(parserConfig != null, "parserConfig must not be null")
}

case class ServerConfig(
  // ServerConfig
  host: String = "localhost",
  port: Int = 8080,
  serverActorId: String = "spray-can-server",
  serviceActorId: String = "spray-root-service",
  timeoutActorId: String = "spray-root-service",
  timeoutTimeout: Long = 500,
  serverHeader: String = "spray-can/" + SprayCanVersion,

  // must be fast and non-blocking
  streamActorCreator: Option[ChunkedRequestContext => Actor] = None,

  // PeerConfig
  readBufferSize: Int = 8192,
  idleTimeout: Long = 10000,
  reapingCycle: Long = 500,
  requestTimeout: Long = 5000,
  timeoutCycle: Long = 200,
  parserConfig: MessageParserConfig = MessageParserConfig()
) extends PeerConfig {

  require(!serverActorId.isEmpty, "serverActorId must not be empty")
  require(!serviceActorId.isEmpty, "serviceActorId must not be empty")
  require(!timeoutActorId.isEmpty, "timeoutActorId must not be empty")
  require(timeoutTimeout >= 0, "timeoutTimeout must be >= 0 ms")

  def endpoint = new InetSocketAddress(host, port)

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
    host           = config.getString("spray-can.server.host", "localhost"),
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
    timeoutCycle   = config.getLong("spray-can.server.timeout-cycle", 200),
    parserConfig   = MessageParserConfig.fromAkkaConf
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
  timeoutCycle: Long = 200,
  parserConfig: MessageParserConfig = MessageParserConfig()
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
    timeoutCycle   = config.getLong("spray-can.server.timeout-cycle", 200),
    parserConfig   = MessageParserConfig.fromAkkaConf
  )
}

case class MessageParserConfig(
  maxUriLength: Int = 2048,
  maxResponseReasonLength: Int = 64,
  maxHeaderNameLength: Int = 64,
  maxHeaderValueLength: Int = 8192,
  maxHeaderCount: Int = 64,
  maxContentLength: Int = 8192 * 1024, // default entity size limit = 8 MB
  maxChunkExtNameLength: Int = 64,
  maxChunkExtValueLength: Int = 256,
  maxChunkExtCount: Int = 16,
  maxChunkSize: Int = 1024 * 1024   // default chunk size limit = 1 MB
)

object MessageParserConfig {
  lazy val fromAkkaConf = MessageParserConfig(
    maxUriLength            = config.getInt("spray-can.parser.max-uri-length", 2048),
    maxResponseReasonLength = config.getInt("spray-can.parser.max-response-reason-length", 64),
    maxHeaderNameLength     = config.getInt("spray-can.parser.max-header-name-length", 64),
    maxHeaderValueLength    = config.getInt("spray-can.parser.max-header-value-length", 8192),
    maxHeaderCount          = config.getInt("spray-can.parser.max-header-count-length", 64),
    maxContentLength        = config.getInt("spray-can.parser.max-content-length", 8192 * 1024),
    maxChunkExtNameLength   = config.getInt("spray-can.parser.max-chunk-ext-name-length", 64),
    maxChunkExtValueLength  = config.getInt("spray-can.parser.max-chunk-ext-value-length", 256),
    maxChunkExtCount        = config.getInt("spray-can.parser.max-chunk-ext-count", 16),
    maxChunkSize            = config.getInt("spray-can.parser.max-chunk-size", 1024 * 1024)
  )
}