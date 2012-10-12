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

package spray.io

import com.typesafe.config.{ConfigFactory, Config}
import spray.util.ConfigUtils


class IOBridgeSettings(config: Config) {
  protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.io")

  val ThreadName           = c getString  "thread-name"
  val ReadBufferSize       = c getBytes   "read-buffer-size"

  val TcpReceiveBufferSize = c getBytes   "tcp.receive-buffer-size"
  val TcpSendBufferSize    = c getBytes   "tcp.send-buffer-size"
  val TcpKeepAlive         = c getInt     "tcp.keep-alive"
  val TcpNoDelay           = c getInt     "tcp.no-delay"

  require(ReadBufferSize       > 0,  "read-buffer-size must be > 0")
  require(TcpReceiveBufferSize >= 0, "receive-buffer-size must be >= 0")
  require(TcpSendBufferSize    >= 0, "send-buffer-size must be >= 0")
}

object IOBridgeSettings {
  def apply(): IOBridgeSettings = apply(ConfigFactory.load())
  implicit def apply(config: Config): IOBridgeSettings = new IOBridgeSettings(config)
}