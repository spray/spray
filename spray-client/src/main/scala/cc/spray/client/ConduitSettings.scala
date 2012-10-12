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

package spray.client

import com.typesafe.config.{Config, ConfigFactory}
import spray.util.ConfigUtils


class ConduitSettings(config: Config) {
  protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.client")

  val MaxConnections       = c getInt "max-connections"
  val MaxRetries           = c getInt "max-retries"
  val WarnOnIllegalHeaders = c getBoolean "warn-on-illegal-headers"

  require(MaxConnections >  0, "max-connections must be > 0")
  require(MaxRetries     >= 0, "max-retries must be >= 0")
}

object ConduitSettings {
  def apply(): ConduitSettings = apply(ConfigFactory.load())
  implicit def apply(config: Config): ConduitSettings = new ConduitSettings(config)
}