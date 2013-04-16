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
import scala.collection.JavaConverters._
import akka.actor.ActorSystem

case class ParserSettings(
    maxUriLength: Int,
    maxResponseReasonLength: Int,
    maxHeaderNameLength: Int,
    maxHeaderValueLength: Int,
    maxHeaderCount: Int,
    maxContentLength: Int,
    maxChunkExtNameLength: Int,
    maxChunkExtValueLength: Int,
    maxChunkExtCount: Int,
    maxChunkSize: Int,
    headerValueCacheLimits: Map[String, Int]) {

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

  val defaultHeaderValueCacheLimit: Int = headerValueCacheLimits("default")

  def headerValueCacheLimit(headerName: String) =
    headerValueCacheLimits.getOrElse(headerName, defaultHeaderValueCacheLimit)
}

object ParserSettings {
  def apply(system: ActorSystem): ParserSettings =
    apply(system.settings.config getConfig "spray.can.parsing")

  def apply(config: Config): ParserSettings = {
    def bytes(key: String): Int = {
      val value: Long = config getBytes key
      if (value <= Int.MaxValue) value.toInt
      else sys.error(s"ParserSettings config setting $key must not be larger than ${Int.MaxValue}")
    }
    val cacheConfig = config.getConfig("header-cache")
    ParserSettings(
      bytes("max-uri-length"),
      bytes("max-response-reason-length"),
      bytes("max-header-name-length"),
      bytes("max-header-value-length"),
      bytes("max-header-count"),
      bytes("max-content-length"),
      bytes("max-chunk-ext-name-length"),
      bytes("max-chunk-ext-value-length"),
      bytes("max-chunk-ext-count"),
      bytes("max-chunk-size"),
      cacheConfig.entrySet.asScala.map(kvp ⇒ kvp.getKey -> cacheConfig.getInt(kvp.getKey))(collection.breakOut))
  }
}
