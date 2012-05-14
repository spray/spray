/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.can.client

import cc.spray.can.parsing.ParserSettings
import com.typesafe.config.{ConfigFactory, Config}

private[can] class ClientSettings(config: Config = ConfigFactory.load) {
  private[this] val c: Config = {
    val c = config.withFallback(ConfigFactory.defaultReference)
    c.checkValid(ConfigFactory.defaultReference, "spray.can.client")
    c.getConfig("spray.can.client")
  }
  val UserAgentHeader               = c getString       "user-agent-header"
  val SSLEncryption                 = c getBoolean      "ssl-encryption"
  val IdleTimeout                   = c getMilliseconds "idle-timeout"
  val RequestTimeout                = c getMilliseconds "request-timeout"
  val ReapingCycle                  = c getMilliseconds "reaping-cycle"
  val AckSends                      = c getBoolean      "ack-sends"
  val ResponseChunkAggregationLimit = c getBytes        "response-chunk-aggregation-limit"
  val RequestSizeHint               = c getBytes        "request-size-hint"

  val ParserSettings = new ParserSettings(c.getConfig("parsing"))

  require(IdleTimeout    >= 0, "idle-timeout must be >= 0")
  require(RequestTimeout >= 0, "request-timeout must be >= 0")
  require(ReapingCycle   >= 0, "reaping-cycle must be >= 0")
  require(0 <= ResponseChunkAggregationLimit && ResponseChunkAggregationLimit <= Int.MaxValue,
    "response-chunk-aggregation-limit must be >= 0 and <= Int.MaxValue")
  require(0 <= RequestSizeHint && RequestSizeHint <= Int.MaxValue,
    "request-size-hint must be >= 0 and <= Int.MaxValue")
}
