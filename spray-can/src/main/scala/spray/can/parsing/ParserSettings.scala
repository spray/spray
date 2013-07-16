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
import spray.http.Uri
import spray.util._

case class ParserSettings(
    maxUriLength: Int,
    maxResponseReasonLength: Int,
    maxHeaderNameLength: Int,
    maxHeaderValueLength: Int,
    maxHeaderCount: Int,
    maxContentLength: Int,
    maxChunkExtLength: Int,
    maxChunkSize: Int,
    uriParsingMode: Uri.ParsingMode,
    illegalHeaderWarnings: Boolean,
    headerValueCacheLimits: Map[String, Int]) {

  require(maxUriLength > 0, "max-uri-length must be > 0")
  require(maxResponseReasonLength > 0, "max-response-reason-length must be > 0")
  require(maxHeaderNameLength > 0, "max-header-name-length must be > 0")
  require(maxHeaderValueLength > 0, "max-header-value-length must be > 0")
  require(maxHeaderCount > 0, "max-header-count must be > 0")
  require(maxContentLength > 0, "max-content-length must be > 0")
  require(maxChunkExtLength > 0, "max-chunk-ext-length must be > 0")
  require(maxChunkSize > 0, "max-chunk-size must be > 0")

  val defaultHeaderValueCacheLimit: Int = headerValueCacheLimits("default")

  def headerValueCacheLimit(headerName: String) =
    headerValueCacheLimits.getOrElse(headerName, defaultHeaderValueCacheLimit)
}

object ParserSettings extends SettingsCompanion[ParserSettings]("spray.can.parsing") {
  def fromSubConfig(c: Config) = {
    val cacheConfig = c getConfig "header-cache"
    apply(
      c getIntBytes "max-uri-length",
      c getIntBytes "max-response-reason-length",
      c getIntBytes "max-header-name-length",
      c getIntBytes "max-header-value-length",
      c getIntBytes "max-header-count",
      c getIntBytes "max-content-length",
      c getIntBytes "max-chunk-ext-length",
      c getIntBytes "max-chunk-size",
      Uri.ParsingMode(c getString "uri-parsing-mode"),
      c getBoolean "illegal-header-warnings",
      cacheConfig.entrySet.asScala.map(kvp ⇒ kvp.getKey -> cacheConfig.getInt(kvp.getKey))(collection.breakOut))
  }
}