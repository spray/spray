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

package spray.util

import com.typesafe.config.{ ConfigFactory, Config }
import akka.actor.ActorSystem

case class UtilSettings(
    logActorPathsWithDots: Boolean,
    logActorSystemName: Boolean) {
}

object UtilSettings {
  def apply(system: ActorSystem): UtilSettings =
    apply(system.settings.config getConfig "spray.util")

  def apply(config: Config): UtilSettings = {
    val c = config withFallback ConfigFactory.defaultReference(getClass.getClassLoader)
    UtilSettings(
      c getBoolean "log-actor-paths-with-dots",
      c getBoolean "log-actor-system-name")
  }
}