/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.can.server

import com.typesafe.config.{ ConfigFactory, Config }
import akka.util.Duration
import akka.actor.ActorSystem
import spray.can.parsing.ParserSettings
import spray.util._

case class ServerSettings(
    serverHeader: String,
    sslEncryption: Boolean,
    pipeliningLimit: Int,
    idleTimeout: Duration,
    requestTimeout: Duration,
    timeoutTimeout: Duration,
    reapingCycle: Duration,
    statsSupport: Boolean,
    remoteAddressHeader: Boolean,
    transparentHeadRequests: Boolean,
    timeoutHandler: String,
    chunklessStreaming: Boolean,
    verboseErrorMessages: Boolean,
    requestChunkAggregationLimit: Int,
    responseSizeHint: Int,
    bindTimeout: Duration,
    unbindTimeout: Duration,
    registrationTimeout: Duration,
    parserSettings: ParserSettings) {

  requirePositiveOrUndefined(idleTimeout)
  requirePositiveOrUndefined(requestTimeout)
  requirePositiveOrUndefined(timeoutTimeout)
  requirePositiveOrUndefined(idleTimeout)
  require(0 <= pipeliningLimit && pipeliningLimit <= 128, "pipelining-limit must be >= 0 and <= 128")
  require(0 <= requestChunkAggregationLimit && requestChunkAggregationLimit <= Int.MaxValue,
    "request-chunk-aggregation-limit must be >= 0 and <= Int.MaxValue")
  require(0 <= responseSizeHint && responseSizeHint <= Int.MaxValue,
    "response-size-hint must be >= 0 and <= Int.MaxValue")
  requirePositiveOrUndefined(bindTimeout)
  requirePositiveOrUndefined(unbindTimeout)
  requirePositiveOrUndefined(registrationTimeout)

  require(!requestTimeout.isFinite || idleTimeout > requestTimeout,
    "idle-timeout must be > request-timeout (if the latter is not 'infinite')")
}

object ServerSettings {
  def apply(system: ActorSystem): ServerSettings =
    apply(system.settings.config getConfig "spray.can.server")

  def apply(config: Config): ServerSettings = {
    val c = config
      .withFallback(Utils.sprayConfigAdditions)
      .withFallback(ConfigFactory.defaultReference(getClass.getClassLoader))
    ServerSettings(
      c getString "server-header",
      c getBoolean "ssl-encryption",
      c.getString("pipelining-limit") match { case "disabled" ⇒ 0; case _ ⇒ c getInt "pipelining-limit" },
      c getDuration "idle-timeout",
      c getDuration "request-timeout",
      c getDuration "timeout-timeout",
      c getDuration "reaping-cycle",
      c getBoolean "stats-support",
      c getBoolean "remote-address-header",
      c getBoolean "transparent-head-requests",
      c getString "timeout-handler",
      c getBoolean "chunkless-streaming",
      c getBoolean "verbose-error-messages",
      c getBytes "request-chunk-aggregation-limit" toInt,
      c getBytes "response-size-hint" toInt,
      c getDuration "bind-timeout",
      c getDuration "unbind-timeout",
      c getDuration "registration-timeout",
      ParserSettings(c getConfig "parsing"))
  }
}
