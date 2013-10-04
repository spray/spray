/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import com.typesafe.config.Config
import akka.util.Duration
import spray.http.Uri
import spray.util._

case class ConnectorSettings(
    bootClass: String,
    requestTimeout: Duration,
    timeoutTimeout: Duration,
    timeoutHandler: String,
    rootPath: Uri.Path,
    remoteAddressHeader: Boolean,
    verboseErrorMessages: Boolean,
    maxContentLength: Long,
    servletRequestAccess: Boolean,
    illegalHeaderWarnings: Boolean) {

  require(!bootClass.isEmpty,
    "No boot class configured. Please specify a boot class FQN in the spray.servlet.boot-class config setting.")
  requirePositive(requestTimeout)
  requirePositive(timeoutTimeout)
  require(maxContentLength > 0, "max-content-length must be > 0")

  val rootPathCharCount = rootPath.charCount
}

object ConnectorSettings extends SettingsCompanion[ConnectorSettings]("spray.servlet") {
  def fromSubConfig(c: Config) = apply(
    c getString "boot-class",
    c getDuration "request-timeout",
    c getDuration "timeout-timeout",
    c getString "timeout-handler",
    Uri.Path(c getString "root-path"),
    c getBoolean "remote-address-header",
    c getBoolean "verbose-error-messages",
    c getBytes "max-content-length",
    c getBoolean "servlet-request-access",
    c getBoolean "illegal-header-warnings")
}
