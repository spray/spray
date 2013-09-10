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

package spray.site

import com.typesafe.config.Config
import scala.collection.JavaConverters._
import spray.util.SettingsCompanion

case class SiteSettings(
    interface: String,
    port: Int,
    devMode: Boolean,
    repoDirs: List[String],
    nightliesDir: String,
    mainVersion: String,
    otherVersions: Seq[String]) {

  require(interface.nonEmpty, "interface must be non-empty")
  require(0 < port && port < 65536, "illegal port")
}

object SiteSettings extends SettingsCompanion[SiteSettings]("spray.site") {
  def fromSubConfig(c: Config) = apply(
    c getString "interface",
    c getInt "port",
    c getBoolean "dev-mode",
    c getString "repo-dirs" split ':' toList,
    c getString "nightlies-dir",
    c getString "main-version",
    c.getStringList("other-versions").asScala)
}