/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import spray.can.parsing.ParserSettings
import spray.http.parser.HttpParser
import spray.http.HttpHeaders._
import spray.util._

case class BackpressureSettings(noAckRate: Int, readingLowWatermark: Int)

case class ServerSettings(
    serverHeader: String,
    sslEncryption: Boolean,
    pipeliningLimit: Int,
    timeouts: ServerSettings.Timeouts,
    reapingCycle: Duration,
    statsSupport: Boolean,
    remoteAddressHeader: Boolean,
    rawRequestUriHeader: Boolean,
    transparentHeadRequests: Boolean,
    timeoutHandler: String,
    chunklessStreaming: Boolean,
    verboseErrorMessages: Boolean,
    requestChunkAggregationLimit: Int,
    responseHeaderSizeHint: Int,
    maxEncryptionChunkSize: Int,
    defaultHostHeader: Host,
    backpressureSettings: Option[BackpressureSettings],
    sslTracing: Boolean,
    parserSettings: ParserSettings) {

  requirePositive(reapingCycle)
  require(0 <= pipeliningLimit && pipeliningLimit <= 128, "pipelining-limit must be >= 0 and <= 128")
  require(0 <= requestChunkAggregationLimit, "request-chunk-aggregation-limit must be >= 0")
  require(0 <= responseHeaderSizeHint, "response-size-hint must be > 0")
  require(0 < maxEncryptionChunkSize, "max-encryption-chunk-size must be > 0")

  def autoBackPressureEnabled: Boolean = backpressureSettings.isDefined && (pipeliningLimit > 1)
}

object ServerSettings extends SettingsCompanion[ServerSettings]("spray.can.server") {
  case class Timeouts(idleTimeout: Duration,
                      requestTimeout: Duration,
                      timeoutTimeout: Duration,
                      chunkHandlerRegistrationTimeout: Duration,
                      bindTimeout: Duration,
                      unbindTimeout: Duration,
                      registrationTimeout: Duration,
                      parsingErrorAbortTimeout: Duration) {
    requirePositive(idleTimeout)
    requirePositive(requestTimeout)
    requirePositive(timeoutTimeout)
    requirePositive(bindTimeout)
    requirePositive(unbindTimeout)
    requirePositive(registrationTimeout)
    require(!requestTimeout.isFinite || idleTimeout > requestTimeout,
      "idle-timeout must be > request-timeout (if the latter is not 'infinite')")
    require(!idleTimeout.isFinite || idleTimeout > 1.second, // the current implementation is not fit for
      "an idle-timeout < 1 second is not supported") // very short idle-timeout settings
  }
  implicit def timeoutsShortcut(s: ServerSettings): Timeouts = s.timeouts

  def fromSubConfig(c: Config) = apply(
    c getString "server-header",
    c getBoolean "ssl-encryption",
    c.getString("pipelining-limit") match { case "disabled" ⇒ 0; case _ ⇒ c getInt "pipelining-limit" },
    Timeouts(
      c getDuration "idle-timeout",
      c getDuration "request-timeout",
      c getDuration "timeout-timeout",
      c getDuration "chunkhandler-registration-timeout",
      c getDuration "bind-timeout",
      c getDuration "unbind-timeout",
      c getDuration "registration-timeout",
      c getDuration "parsing-error-abort-timeout"),
    c getDuration "reaping-cycle",
    c getBoolean "stats-support",
    c getBoolean "remote-address-header",
    c getBoolean "raw-request-uri-header",
    c getBoolean "transparent-head-requests",
    c getString "timeout-handler",
    c getBoolean "chunkless-streaming",
    c getBoolean "verbose-error-messages",
    c getIntBytes "request-chunk-aggregation-limit",
    c getIntBytes "response-header-size-hint",
    c getIntBytes "max-encryption-chunk-size",
    defaultHostHeader =
      HttpParser.parseHeader(RawHeader("Host", c getString "default-host-header")) match {
        case Right(x: Host) ⇒ x
        case Left(error)    ⇒ sys.error(error.withSummary("Configured `default-host-header` is illegal").formatPretty)
        case Right(_)       ⇒ throw new IllegalStateException
      },
    backpressureSettings =
      if (c.getBoolean("automatic-back-pressure-handling"))
        Some(BackpressureSettings(
        c getInt "back-pressure.noack-rate",
        c getPossiblyInfiniteInt "back-pressure.reading-low-watermark"))
      else None,
    c getBoolean "ssl-tracing",
    ParserSettings fromSubConfig c.getConfig("parsing"))
}
