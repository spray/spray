/*
 * Copyright (C) 2011. 2012 Mathias Doenitz
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

package cc.spray.can
package config

import akka.util.Duration
import cc.spray.io.IoWorkerConfig

case class HttpServerConfig(

  // HttpServer
  serverHeader: String = "spray-can/" + SprayCanVersion,
  idleTimeout: Duration = Duration("10 sec"),
  requestTimeout: Duration = Duration("5 sec"),
  reapingCycle: Duration = Duration("100 ms"),
  directSender: Boolean = false,

  // IoWorkerConfig
  threadName: String  = IoWorkerConfig.defaultThreadName,
  readBufferSize: Int = IoWorkerConfig.defaultReadBufferSize,

  tcpReceiveBufferSize: Option[Int] = IoWorkerConfig.defaultTcpReceiveBufferSize,
  tcpSendBufferSize: Option[Int] = IoWorkerConfig.defaultTcpSendBufferSize,
  tcpKeepAlive: Option[Boolean] = IoWorkerConfig.defaultTcpKeepAlive,
  tcpNoDelay: Option[Boolean] = IoWorkerConfig.defaultTcpNoDelay,

  // HttpParserConfig
  maxUriLength: Int             = HttpParserConfig.defaultMaxUriLength,
  maxResponseReasonLength: Int  = HttpParserConfig.defaultMaxResponseReasonLength,
  maxHeaderNameLength: Int      = HttpParserConfig.defaultMaxHeaderNameLength,
  maxHeaderValueLength: Int     = HttpParserConfig.defaultMaxHeaderValueLength,
  maxHeaderCount: Int           = HttpParserConfig.defaultMaxHeaderCount,
  maxContentLength: Int         = HttpParserConfig.defaultMaxContentLength,
  maxChunkExtNameLength: Int    = HttpParserConfig.defaultMaxChunkExtNameLength,
  maxChunkExtValueLength: Int   = HttpParserConfig.defaultMaxChunkExtValueLength,
  maxChunkExtCount: Int         = HttpParserConfig.defaultMaxChunkExtCount,
  maxChunkSize: Int             = HttpParserConfig.defaultMaxChunkSize

) extends IoWorkerConfig with HttpParserConfig {
  require(idleTimeout >= Duration.Zero, "idleTimeout must be >= 0")
  require(requestTimeout >= Duration.Zero, "requestTimeout must be >= 0")
  require(reapingCycle >= Duration.Zero, "reapingCycle must be >= 0")
  require(readBufferSize > 0, "readBufferSize must be > 0")
  require(tcpReceiveBufferSize.isEmpty || tcpReceiveBufferSize.get > 0, "tcpReceiveBufferSize must be > 0 if defined")
  require(tcpSendBufferSize.isEmpty || tcpSendBufferSize.get > 0, "tcpSendBufferSize must be > 0 if defined")
  require(maxUriLength > 0, "maxUriLength must be > 0")
  require(maxResponseReasonLength > 0, "maxResponseReasonLength must be > 0")
  require(maxHeaderNameLength > 0, "maxHeaderNameLength must be > 0")
  require(maxHeaderValueLength > 0, "maxHeaderValueLength must be > 0")
  require(maxHeaderCount > 0, "maxHeaderCount must be > 0")
  require(maxContentLength > 0, "maxContentLength must be > 0")
  require(maxChunkExtNameLength > 0, "maxChunkExtNameLength must be > 0")
  require(maxChunkExtValueLength > 0, "maxChunkExtValueLength must be > 0")
  require(maxChunkExtCount > 0, "maxChunkExtCount must be > 0")
  require(maxChunkSize > 0, "maxChunkSize must be > 0")

  def idleTimeoutEnabled = idleTimeout > Duration.Zero
  def requestTimeoutEnabled = requestTimeout > Duration.Zero
}
