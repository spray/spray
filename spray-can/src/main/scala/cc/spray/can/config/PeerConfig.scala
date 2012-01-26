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
 * The common configuration elements of a [[cc.spray.can.ServerConfig]] and a [[cc.spray.can.ClientConfig]].
 */
trait PeerConfig {

  /**
   * The size of the read buffer used for processing incoming messages.
   * Usually there should be little reason to configure it to a value different from its default of 8192 (bytes).
   */
  def readBufferSize: Int

  /**
   * The time period in milliseconds that an open HTTP connection has to be idle before automatically being closed.
   * Set to zero to disable connection timeouts.
   *
   * Default: 10,000 ms = 10 seconds
   */
  def idleTimeout: Long

  /**
   * The `reapingCycle` is the time in milliseconds between two runs of the "reaper", which is the logic that closes
   * open HTTP connections whose idle timeout has exceeded the configured value. Larger values (very slightly) increase
   * overall server or client efficiency (since less time is being spent looking for timed out connections) whereas
   * smaller values increase the precision with which idle connections are really closed after the configured idle
   * timeout. The default value is 500, which means that the reaper runs twice per second.
   */
  def reapingCycle: Long

  /**
   * The time period in milliseconds that are response has to be produced by the application (in the case of the
   * [[cc.spray.can.ServerConfig]]) or received by the server (in the case of the [[cc.spray.can.ClientConfig]].
   * Set to zero to disable request timeouts.
   * The default value is 5000 ms = 5 seconds.
   */
  def requestTimeout: Long

  /**
   * The `timeoutCycle` is the time in milliseconds between two runs of the logic that determines which of all open
   * requests have timed out. Larger values (very slightly) increase overall server or client efficiency (since less
   * time is being spent looking for timed out requests) whereas smaller values increase the precision with which
   * timed out requests are really reacted on after the configured timeout time has elapsed.
   * The default value is 200.
   */
  def timeoutCycle: Long

  /**
   * The configuration of the ''spray-can'' message parser.
   */
  def parserConfig: HttpParserConfig

  require(readBufferSize > 0, "readBufferSize must be > 0 bytes")
  require(idleTimeout >= 0, "idleTimeout must be >= 0 ms")
  require(reapingCycle > 0, "reapingCycle must be > 0 ms")
  require(requestTimeout >= 0, "requestTimeout must be >= 0 ms")
  require(timeoutCycle > 0, "timeoutCycle must be > 0 ms")
  require(parserConfig != null, "parserConfig must not be null")
}