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

import com.typesafe.config.{ConfigFactory, Config}
import spray.can.parsing.ParserSettings
import spray.util.ConfigUtils


class ServerSettings(config: Config = ConfigFactory.load) {
  protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.can.server")

  val ServerHeader                  = c getString       "server-header"
  val SSLEncryption                 = c getBoolean      "ssl-encryption"
  val PipeliningLimit               = c getInt          "pipelining-limit"
  val IdleTimeout                   = c getMilliseconds "idle-timeout"
  val RequestTimeout                = c getMilliseconds "request-timeout"
  val TimeoutTimeout                = c getMilliseconds "timeout-timeout"
  val ReapingCycle                  = c getMilliseconds "reaping-cycle"
  val StatsSupport                  = c getBoolean      "stats-support"
  val RemoteAddressHeader           = c getBoolean      "remote-address-header"
  val TransparentHeadRequests       = c getBoolean      "transparent-head-requests"
  val TimeoutHandler                = c getString       "timeout-handler"
  val ChunklessStreaming            = c getBoolean      "chunkless-streaming"
  val VerboseErrorMessages          = c getBoolean      "verbose-error-messages"
  val RequestChunkAggregationLimit  = c getBytes        "request-chunk-aggregation-limit"
  val ResponseSizeHint              = c getBytes        "response-size-hint"

  val ParserSettings = new ParserSettings(c.getConfig("parsing"))

  require(0 < PipeliningLimit && PipeliningLimit <= 16, "pipelining-limit must be > 0 and <= 16")
  require(IdleTimeout     >= 0, "idle-timeout must be >= 0")
  require(RequestTimeout  >= 0, "request-timeout must be >= 0")
  require(TimeoutTimeout  >= 0, "timeout-timeout must be >= 0")
  require(ReapingCycle    >= 0, "reaping-cycle must be >= 0")
  require(0 <= RequestChunkAggregationLimit && RequestChunkAggregationLimit <= Int.MaxValue,
    "request-chunk-aggregation-limit must be >= 0 and <= Int.MaxValue")
  require(0 <= ResponseSizeHint && ResponseSizeHint <= Int.MaxValue,
    "response-size-hint must be >= 0 and <= Int.MaxValue")
}

object ServerSettings {
  def apply(): ServerSettings = apply(ConfigFactory.load())
  implicit def apply(config: Config): ServerSettings = new ServerSettings(config)
}