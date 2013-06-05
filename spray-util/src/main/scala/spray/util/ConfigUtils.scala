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

package spray.util

import com.typesafe.config.{Config, ConfigFactory}
import collection.JavaConverters._
import java.net.InetAddress


object ConfigUtils {

  def prepareSubConfig(config: Config, path: String): Config = {
    val c = config.withFallback(referenceConfig)
    c.checkValid(referenceConfig, path)
    c.getConfig(path)
  }

  lazy val referenceConfig = ConfigFactory.defaultReference withFallback sprayConfigAdditions

  def sprayConfigAdditions: Config = ConfigFactory.parseMap {
    Map("spray.hostname" -> tryOrElse(InetAddress.getLocalHost.getHostName, _ => "")).asJava
  }
}