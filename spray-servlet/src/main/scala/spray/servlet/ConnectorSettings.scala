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

package spray.servlet

import spray.util.ConfigUtils
import com.typesafe.config.Config


class ConnectorSettings(config: Config) {
  protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.servlet")

  val BootClass            = c getString       "boot-class"
  val RequestTimeout       = c getMilliseconds "request-timeout"
  val TimeoutTimeout       = c getMilliseconds "timeout-timeout"
  val RootPath             = c getString       "root-path"
  val TimeoutHandler       = c getString       "timeout-handler"
  val RemoteAddressHeader  = c getBoolean      "remote-address-header"
  val VerboseErrorMessages = c getBoolean      "verbose-error-messages"
  val MaxContentLength     = c getBytes        "max-content-length"

  require(!BootClass.isEmpty,
    "No boot class configured. Please specify a boot class FQN in the spray.servlet.boot-class config setting.")
  require(RequestTimeout >= 0, "request-timeout must be >= 0")
  require(TimeoutTimeout >= 0, "timeout-timeout must be >= 0")
  require(MaxContentLength > 0, "max-content-length must be > 0")
}