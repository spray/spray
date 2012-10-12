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

package spray.can.client

import com.typesafe.config.{ConfigFactory, Config}
import spray.can.parsing.ParserSettings
import spray.util.ConfigUtils


class ClientSettings(config: Config) {
  protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.can.client")

  val UserAgentHeader               = c getString       "user-agent-header"
  val SSLEncryption                 = c getBoolean      "ssl-encryption"
  val IdleTimeout                   = c getMilliseconds "idle-timeout"
  val RequestTimeout                = c getMilliseconds "request-timeout"
  val ReapingCycle                  = c getMilliseconds "reaping-cycle"
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

object ClientSettings {
  def apply(): ClientSettings = apply(ConfigFactory.load())
  implicit def apply(config: Config): ClientSettings = new ClientSettings(config)
}
