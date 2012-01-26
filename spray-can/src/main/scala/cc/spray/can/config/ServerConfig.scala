/*
 * Copyright (C) 2011. 2012 Mathias Doenitz
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
package config

import java.net.InetSocketAddress
import akka.actor.Actor

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
  import akka.config.Config.config._

  /**
   * Returns a `ServerConfig` constructed from the `spray-can` section of the applications `akka.conf` file.
   */
  lazy val fromAkkaConf = ServerConfig(
    // ServerConfig
    host           = getString("spray-can.server.host", "localhost"),
    port           = getInt("spray-can.server.port", 8080),
    serverActorId  = getString("spray-can.server.server-actor-id", "spray-can-server"),
    serviceActorId = getString("spray-can.server.service-actor-id", "spray-root-service"),
    timeoutActorId = getString("spray-can.server.timeout-actor-id", "spray-root-service"),
    timeoutTimeout = getLong("spray-can.server.timeout-timeout", 500),
    serverHeader   = getString("spray-can.server.server-header", "spray-can/" + SprayCanVersion),

    // PeerConfig
    readBufferSize = getInt("spray-can.server.read-buffer-size", 8192),
    idleTimeout    = getLong("spray-can.server.idle-timeout", 10000),
    reapingCycle   = getLong("spray-can.server.reaping-cycle", 500),
    requestTimeout = getLong("spray-can.server.request-timeout", 5000),
    timeoutCycle   = getLong("spray-can.server.timeout-cycle", 200),
    parserConfig   = MessageParserConfig.fromAkkaConf
  )
}