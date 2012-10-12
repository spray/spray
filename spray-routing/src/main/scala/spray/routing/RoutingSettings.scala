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

package spray.routing

import com.typesafe.config.{ConfigFactory, Config}
import spray.util.ConfigUtils


class RoutingSettings(config: Config) {
  protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.routing")

  val RelaxedHeaderParsing      = c getBoolean "relaxed-header-parsing"
  val VerboseErrorMessages      = c getBoolean "verbose-error-messages"
  val FileChunkingThresholdSize = c getBytes   "file-chunking-threshold-size"
  val FileChunkingChunkSize     = c getBytes   "file-chunking-chunk-size"
  val Users                     = c getConfig  "users"

  require(FileChunkingThresholdSize >= 0, "file-chunking-threshold-size must be >= 0")
  require(FileChunkingChunkSize     > 0, "file-chunking-chunk-size must be > 0")
}

object RoutingSettings {
  implicit val Default: RoutingSettings = apply(ConfigFactory.load())
  implicit def apply(config: Config): RoutingSettings = new RoutingSettings(config)
}