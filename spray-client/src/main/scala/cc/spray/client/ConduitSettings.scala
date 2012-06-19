/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.client

import com.typesafe.config.{Config, ConfigFactory}


class ConduitSettings(config: Config = ConfigFactory.load()) {
  private[this] val c: Config = {
    val c = config.withFallback(ConfigFactory.defaultReference)
    c.checkValid(ConfigFactory.defaultReference, "spray.client")
    c.getConfig("spray.client")
  }

  val MaxConnections = c getInt "max-connections"
  val MaxRetries     = c getInt "max-retries"

  require(MaxConnections >  0, "max-connections must be > 0")
  require(MaxRetries     >= 0, "max-retries must be >= 0")
}
