/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can.config

/**
 * The configuration of the HTTP message parsers.
 * The only setting that more frequently requires tweaking is the `maxContentLength` setting, which represents the
 * maximum request entity size of an HTTP request or response accepted by the server or client.
 */
case class HttpParserConfig(
  maxUriLength: Int = 2048,
  maxResponseReasonLength: Int = 64,
  maxHeaderNameLength: Int = 64,
  maxHeaderValueLength: Int = 8192,
  maxHeaderCount: Int = 64,
  maxContentLength: Int = 8192 * 1024, // default entity size limit = 8 MB
  maxChunkExtNameLength: Int = 64,
  maxChunkExtValueLength: Int = 256,
  maxChunkExtCount: Int = 16,
  maxChunkSize: Int = 1024 * 1024   // default chunk size limit = 1 MB
)

object HttpParserConfig {
  import akka.config.Config.config._

  lazy val fromAkkaConf = HttpParserConfig(
    maxUriLength            = getInt("spray-can.parser.max-uri-length", 2048),
    maxResponseReasonLength = getInt("spray-can.parser.max-response-reason-length", 64),
    maxHeaderNameLength     = getInt("spray-can.parser.max-header-name-length", 64),
    maxHeaderValueLength    = getInt("spray-can.parser.max-header-value-length", 8192),
    maxHeaderCount          = getInt("spray-can.parser.max-header-count-length", 64),
    maxContentLength        = getInt("spray-can.parser.max-content-length", 8192 * 1024),
    maxChunkExtNameLength   = getInt("spray-can.parser.max-chunk-ext-name-length", 64),
    maxChunkExtValueLength  = getInt("spray-can.parser.max-chunk-ext-value-length", 256),
    maxChunkExtCount        = getInt("spray-can.parser.max-chunk-ext-count", 16),
    maxChunkSize            = getInt("spray-can.parser.max-chunk-size", 1024 * 1024)
  )
}