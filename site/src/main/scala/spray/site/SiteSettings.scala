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

package spray.site

import com.typesafe.config.{ Config, ConfigFactory }
import akka.actor.ActorSystem

case class SiteSettings(
    interface: String,
    port: Int,
    devMode: Boolean,
    repoDirs: List[String],
    nightliesDir: String) {

  require(interface.nonEmpty, "interface must be non-empty")
  require(0 < port && port < 65536, "illegal port")
}

object SiteSettings {
  def apply(system: ActorSystem): SiteSettings =
    apply(system.settings.config getConfig "spray.site")

  def apply(config: Config): SiteSettings = {
    val c = config withFallback ConfigFactory.defaultReference(getClass.getClassLoader)
    SiteSettings(
      c getString "interface",
      c getInt "port",
      c getBoolean "dev-mode",
      c getString "repo-dirs" split ':' toList,
      c getString "nightlies-dir")
  }
}