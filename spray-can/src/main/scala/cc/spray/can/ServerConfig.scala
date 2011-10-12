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

/**
 * The common configuration elements of a [[cc.spray.can.ServerConfig]] and a [[cc.spray.can.ClientConfig]].
 */
trait PeerConfig {
  /**
   * The size of the read buffer used for processing incoming messages.
   * Usually there should be little reason to configure it to a value different from its default of 8192 (bytes).
   */
  def readBufferSize: Int

  /**
   * The time period in milliseconds that an open HTTP connection has to be idle before automatically being closed.
   * Set to zero to disable connection timeouts.
   *
   * Default: 10,000 ms = 10 seconds
   */
  def idleTimeout: Long

  /**
   * The `reapingCycle` is the time in milliseconds between two runs of the "reaper", which is the logic that closes
   * open HTTP connections whose idle timeout has exceeded the configured value. Larger values (very slightly) increase
   * overall server or client efficiency (since less time is being spent looking for timed out connections) whereas
   * smaller values increase the precision with which idle connections are really closed after the configured idle
   * timeout. The default value is 500, which means that the reaper runs twice per second.
   */
  def reapingCycle: Long

  /**
   * The time period in milliseconds that are response has to be produced by the application (in the case of the
   * [[cc.spray.can.ServerConfig]]) or received by the server (in the case of the [[cc.spray.can.ClientConfig]].
   * Set to zero to disable request timeouts.
   * The default value is 5000 ms = 5 seconds.
   */
  def requestTimeout: Long

  /**
   * The `timeoutCycle` is the time in milliseconds between two runs of the logic that determines which of all open
   * requests have timed out. Larger values (very slightly) increase overall server or client efficiency (since less
   * time is being spent looking for timed out requests) whereas smaller values increase the precision with which
   * timed out requests are really reacted on after the configured timeout time has elapsed.
   * The default value is 200.
   */
  def timeoutCycle: Long

  /**
   * The configuration of the ''spray-can'' message parser.
   */
  def parserConfig: MessageParserConfig

  require(readBufferSize > 0, "readBufferSize must be > 0 bytes")
  require(idleTimeout >= 0, "idleTimeout must be >= 0 ms")
  require(reapingCycle > 0, "reapingCycle must be > 0 ms")
  require(requestTimeout >= 0, "requestTimeout must be >= 0 ms")
  require(timeoutCycle > 0, "timeoutCycle must be > 0 ms")
  require(parserConfig != null, "parserConfig must not be null")
}

/**
 * The `ServerConfig` configures an instance of the [[cc.spray.can.HttpServer]] actor.
 *
 * @constructor Creates a new `ServerConfig`
 * @param host the interface to bind to, default is `localhost`
 * @param port the port to bind to, default is `8080`
 * @param serverActorId the actor id the [[cc.spray.can.HttpServer]] is to receive, default is `spray-can-server`
 * @param serviceActorId the id of the actor to dispatch the generated [[cc.spray.can.RequestContext]] messages to,
 * default is `spray-root-service`
 * @param timeoutActorId the id of the actor to dispatch the generated [[cc.spray.can.Timeout]] messages to,
 * default is `spray-root-service`
 * @param timeoutTimeout the number of milliseconds the timeout actor has to complete the request after having received
 * a [[cc.spray.can.Timeout]] message. If this time has elapsed without the request being completed the `HttpServer`
 * completes the request by calling its `timeoutTimeoutResponse` method.
 * @param serverHeader the value of the "Server" response header set by the [[cc.spray.can.HttpServer]], if empty the
 * "User-Agent" header will not be rendered
 * @param streamActorCreator an optional function creating a "stream actor", an per-request actor receiving the parts
 * of a chunked (streaming) request as separate messages. If `None` the [[cc.spray.can.HttpServer]] will use a new
 * [[cc.spray.can.BufferingRequestStreamActor]] instance for every incoming chunked request, which buffers chunked
 * content before dispatching it as a regular [[cc.spray.can.RequestContext]] to the service actor.
 */
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

  /**
   * Returns a `ServerConfig` constructed from the `spray-can` section of the applications `akka.conf` file.
   */
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

/**
 * The `ClientConfig` configures an instance of the [[cc.spray.can.HttpClient]] actor.
 *
 * @constructor Creates a new `ClientConfig`
 * @param clientActorId the actor id the [[cc.spray.can.HttpClient]] is to receive, default is `spray-can-server`
 * @param userAgentHeader the value of the "User-Agent" request header set by the [[cc.spray.can.HttpClient]],
 * if empty the "User-Agent" request header will not be rendered
 */
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

  require(!clientActorId.isEmpty, "clientActorId must not be empty")

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

/**
 * The configuration of the HTTP message parser.
 * The only setting that more frequently requires tweaking is the `maxContentLength` setting, which represents the
 * maximum request entity size of an HTTP request or response accepted by the server or client.
 */
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