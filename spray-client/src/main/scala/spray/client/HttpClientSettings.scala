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


class HttpClientSettings(config: Config) {
  protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.client.http-client")

  val PruningCycle          = c getMilliseconds "pruning-cycle"
  val PruningShare          = c getDouble       "pruning-share"
  val PruningSelectionLimit = c getDouble       "pruning-selection-limit"

  require(PruningCycle >= 0, "pruning-cycle must be >= 0")
  require(0.0 <= PruningShare && PruningShare <= 1.0, "pruning-cycle must be >= 0.0 and <= 1.0")
  require(PruningSelectionLimit > 0, "pruning-selection-limit must be > 0")
}

object HttpClientSettings {
  implicit def apply(config: Config = ConfigFactory.load()) = new HttpClientSettings(config)
}