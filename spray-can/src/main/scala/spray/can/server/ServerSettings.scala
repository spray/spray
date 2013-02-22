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

package spray.can.server

import com.typesafe.config.Config
import scala.concurrent.duration.Duration
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
  registrationTimeout: Duration,
  parserSettings: ParserSettings) {

  requirePositiveOrUndefined(idleTimeout)
  requirePositiveOrUndefined(requestTimeout)
  requirePositiveOrUndefined(timeoutTimeout)
  requirePositiveOrUndefined(idleTimeout)
  require(0 < pipeliningLimit && pipeliningLimit <= 128, "pipelining-limit must be > 0 and <= 128")
  require(0 <= requestChunkAggregationLimit && requestChunkAggregationLimit <= Int.MaxValue,
    "request-chunk-aggregation-limit must be >= 0 and <= Int.MaxValue")
  require(0 <= responseSizeHint && responseSizeHint <= Int.MaxValue,
    "response-size-hint must be >= 0 and <= Int.MaxValue")
  requirePositiveOrUndefined(bindTimeout)
  requirePositiveOrUndefined(registrationTimeout)
}

object ServerSettings {
  def apply(system: ActorSystem): ServerSettings =
    apply(system.settings.config getConfig "spray.can.server")

  def apply(c: Config): ServerSettings = {
    val config = c withFallback ConfigUtils.sprayConfigAdditions
    ServerSettings(
      config getString   "server-header",
      config getBoolean  "ssl-encryption",
      config getInt      "pipelining-limit",
      config getDuration "idle-timeout",
      config getDuration "request-timeout",
      config getDuration "timeout-timeout",
      config getDuration "reaping-cycle",
      config getBoolean  "stats-support",
      config getBoolean  "remote-address-header",
      config getBoolean  "transparent-head-requests",
      config getString   "timeout-handler",
      config getBoolean  "chunkless-streaming",
      config getBoolean  "verbose-error-messages",
      config getBytes    "request-chunk-aggregation-limit" toInt,
      config getBytes    "response-size-hint" toInt,
      config getDuration "bind-timeout",
      config getDuration "registration-timeout",
      ParserSettings(config getConfig "parsing")
    )
  }
}