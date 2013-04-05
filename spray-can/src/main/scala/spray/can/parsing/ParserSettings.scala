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

package spray.can.parsing

import com.typesafe.config.Config
import akka.actor.ActorSystem

case class ParserSettings(
    maxUriLength: Long,
    maxResponseReasonLength: Long,
    maxHeaderNameLength: Long,
    maxHeaderValueLength: Long,
    maxHeaderCount: Long,
    maxContentLength: Long,
    maxChunkExtNameLength: Long,
    maxChunkExtValueLength: Long,
    maxChunkExtCount: Long,
    maxChunkSize: Long) {

  require(maxUriLength > 0, "max-uri-length must be > 0")
  require(maxResponseReasonLength > 0, "max-response-reason-length must be > 0")
  require(maxHeaderNameLength > 0, "max-header-name-length must be > 0")
  require(maxHeaderValueLength > 0, "max-header-value-length must be > 0")
  require(maxHeaderCount > 0, "max-header-count must be > 0")
  require(maxContentLength > 0, "max-content-length must be > 0")
  require(maxChunkExtNameLength > 0, "max-chunk-ext-name-length must be > 0")
  require(maxChunkExtValueLength > 0, "max-chunk-ext-value-length must be > 0")
  require(maxChunkExtCount > 0, "max-chunk-ext-count must be > 0")
  require(maxChunkSize > 0, "max-chunk-size must be > 0")
}

object ParserSettings {
  def apply(system: ActorSystem): ParserSettings =
    apply(system.settings.config getConfig "spray.can.parsing")

  def apply(config: Config): ParserSettings =
    ParserSettings(
      config getBytes "max-uri-length",
      config getBytes "max-response-reason-length",
      config getBytes "max-header-name-length",
      config getBytes "max-header-value-length",
      config getBytes "max-header-count",
      config getBytes "max-content-length",
      config getBytes "max-chunk-ext-name-length",
      config getBytes "max-chunk-ext-value-length",
      config getBytes "max-chunk-ext-count",
      config getBytes "max-chunk-size")
}
