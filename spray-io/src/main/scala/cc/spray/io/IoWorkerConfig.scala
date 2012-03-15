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

package cc.spray.io

trait IoWorkerConfig {
  def threadName: String
  def readBufferSize: Int

  def tcpReceiveBufferSize: Option[Int]
  def tcpSendBufferSize: Option[Int]
  def tcpKeepAlive: Option[Boolean]
  def tcpNoDelay: Option[Boolean]
}

object IoWorkerConfig {
  val defaultThreadName = "spray-io-worker"
  val defaultReadBufferSize = 4096

  val defaultTcpReceiveBufferSize: Option[Int] = None
  val defaultTcpSendBufferSize: Option[Int] = None
  val defaultTcpKeepAlive: Option[Boolean] = None
  val defaultTcpNoDelay: Option[Boolean] = Some(true)

  def apply(_threadName: String = defaultThreadName,
            _readBufferSize: Int = defaultReadBufferSize,
            _tcpReceiveBufferSize: Option[Int] = defaultTcpReceiveBufferSize,
            _tcpSendBufferSize: Option[Int] = defaultTcpSendBufferSize,
            _tcpKeepAlive: Option[Boolean] = defaultTcpKeepAlive,
            _tcpNoDelay: Option[Boolean] = defaultTcpNoDelay) = {
    new IoWorkerConfig {
      def threadName = _threadName
      def readBufferSize = _readBufferSize
      def tcpReceiveBufferSize = _tcpReceiveBufferSize
      def tcpSendBufferSize = _tcpSendBufferSize
      def tcpKeepAlive = _tcpKeepAlive
      def tcpNoDelay = _tcpNoDelay
    }
  }
}