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

package spray.can.client

import com.typesafe.config.{ ConfigFactory, Config }
import akka.util.Duration
import akka.actor.{ ActorRefFactory, ActorSystem }
import spray.can.parsing.ParserSettings
import spray.util._

case class ClientConnectionSettings(
    userAgentHeader: String,
    sslEncryption: Boolean,
    idleTimeout: Duration,
    requestTimeout: Duration,
    reapingCycle: Duration,
    responseChunkAggregationLimit: Int,
    requestSizeHint: Int,
    connectingTimeout: Duration,
    parserSettings: ParserSettings) {

  requirePositiveOrUndefined(idleTimeout)
  requirePositiveOrUndefined(requestTimeout)
  requirePositiveOrUndefined(reapingCycle)
  require(0 <= responseChunkAggregationLimit && responseChunkAggregationLimit <= Int.MaxValue,
    "response-chunk-aggregation-limit must be >= 0 and <= Int.MaxValue")
  require(0 <= requestSizeHint && requestSizeHint <= Int.MaxValue,
    "request-size-hint must be >= 0 and <= Int.MaxValue")
  requirePositiveOrUndefined(connectingTimeout)
}

object ClientConnectionSettings {
  def apply(system: ActorSystem): ClientConnectionSettings =
    apply(system.settings.config getConfig "spray.can.client")

  def apply(config: Config): ClientConnectionSettings = {
    val c = config
      .withFallback(Utils.sprayConfigAdditions)
      .withFallback(ConfigFactory.defaultReference(getClass.getClassLoader))
    ClientConnectionSettings(
      c getString "user-agent-header",
      c getBoolean "ssl-encryption",
      c getDuration "idle-timeout",
      c getDuration "request-timeout",
      c getDuration "reaping-cycle",
      c getBytes "response-chunk-aggregation-limit" toInt,
      c getBytes "request-size-hint" toInt,
      c getDuration "connecting-timeout",
      ParserSettings(c getConfig "parsing"))
  }

  def apply(optionalSettings: Option[ClientConnectionSettings])(implicit actorRefFactory: ActorRefFactory): ClientConnectionSettings =
    optionalSettings getOrElse apply(actorSystem)
}
