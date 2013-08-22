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

package spray.can.client

import com.typesafe.config.Config
import akka.util.Duration
import spray.util._

case class HostConnectorSettings(
    maxConnections: Int,
    maxRetries: Int,
    pipelining: Boolean,
    idleTimeout: Duration,
    connectionSettings: ClientConnectionSettings) {

  require(maxConnections > 0, "max-connections must be > 0")
  require(maxRetries >= 0, "max-retries must be >= 0")
  requirePositive(idleTimeout)
}

object HostConnectorSettings extends SettingsCompanion[HostConnectorSettings]("spray.can") {
  def fromSubConfig(c: Config) = apply(
    c getInt "host-connector.max-connections",
    c getInt "host-connector.max-retries",
    c getBoolean "host-connector.pipelining",
    c getDuration "host-connector.idle-timeout",
    ClientConnectionSettings fromSubConfig c.getConfig("client"))
}
