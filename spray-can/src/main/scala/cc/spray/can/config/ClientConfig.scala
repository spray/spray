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

package cc.spray.can
package config

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
  parserConfig: HttpParserConfig = HttpParserConfig()
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
  import akka.config.Config.config._

  lazy val fromAkkaConf = ClientConfig(
    // ClientConfig
    clientActorId   = getString("spray-can.client.client-actor-id", "spray-can-client"),
    userAgentHeader = getString("spray-can.client.user-agent-header", "spray-can/" + SprayCanVersion),

    // PeerConfig
    readBufferSize = getInt("spray-can.client.read-buffer-size", 8192),
    idleTimeout    = getLong("spray-can.client.idle-timeout", 10000),
    reapingCycle   = getLong("spray-can.client.reaping-cycle", 500),
    requestTimeout = getLong("spray-can.client.request-timeout", 5000),
    timeoutCycle   = getLong("spray-can.client.timeout-cycle", 200),
    parserConfig   = HttpParserConfig.fromAkkaConf
  )
}