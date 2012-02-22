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
trait HttpParserConfig {
  def maxUriLength: Int
  def maxResponseReasonLength: Int
  def maxHeaderNameLength: Int
  def maxHeaderValueLength: Int
  def maxHeaderCount: Int
  def maxContentLength: Int
  def maxChunkExtNameLength: Int
  def maxChunkExtValueLength: Int
  def maxChunkExtCount: Int
  def maxChunkSize: Int
}

object HttpParserConfig {
  val defaultMaxUriLength = 2048
  val defaultMaxResponseReasonLength = 64
  val defaultMaxHeaderNameLength = 64
  val defaultMaxHeaderValueLength = 8192
  val defaultMaxHeaderCount = 64
  val defaultMaxContentLength = 8192 * 1024 // default entity size limit = 8 MB
  val defaultMaxChunkExtNameLength = 64
  val defaultMaxChunkExtValueLength = 256
  val defaultMaxChunkExtCount = 16
  val defaultMaxChunkSize = 1024 * 1024   // default chunk size limit = 1 MB

  def apply(
    _maxUriLength: Int = defaultMaxUriLength,
    _maxResponseReasonLength: Int = defaultMaxResponseReasonLength,
    _maxHeaderNameLength: Int = defaultMaxHeaderNameLength,
    _maxHeaderValueLength: Int = defaultMaxHeaderValueLength,
    _maxHeaderCount: Int = defaultMaxHeaderCount,
    _maxContentLength: Int = defaultMaxContentLength,
    _maxChunkExtNameLength: Int = defaultMaxChunkExtNameLength,
    _maxChunkExtValueLength: Int = defaultMaxChunkExtValueLength,
    _maxChunkExtCount: Int = defaultMaxChunkExtCount,
    _maxChunkSize: Int = defaultMaxChunkSize
  ) = new HttpParserConfig {
    def maxUriLength = _maxUriLength
    def maxResponseReasonLength = _maxResponseReasonLength
    def maxHeaderNameLength = _maxHeaderNameLength
    def maxHeaderValueLength = _maxHeaderValueLength
    def maxHeaderCount = _maxHeaderCount
    def maxContentLength = _maxContentLength
    def maxChunkExtNameLength = _maxChunkExtNameLength
    def maxChunkExtValueLength = _maxChunkExtValueLength
    def maxChunkExtCount = _maxChunkExtCount
    def maxChunkSize = defaultMaxChunkSize
  }
}