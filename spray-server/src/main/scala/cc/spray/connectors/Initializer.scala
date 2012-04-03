/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.connectors

import javax.servlet.{ServletContextListener, ServletContextEvent}
import cc.spray.util.Spray
import akka.util.Switch
import cc.spray.SprayServletSettings
import akka.config.ConfigurationException

class Initializer extends ServletContextListener {
  private val booted = new Switch(false)

  def contextInitialized(e: ServletContextEvent) {
    booted switchOn {
      println("Starting spray application ...")
      val loader = getClass.getClassLoader
      Thread.currentThread.setContextClassLoader(loader)
      SprayServletSettings.BootClasses match {
        case Nil =>
          throw new ConfigurationException("No boot classes configured. " +
              "Please specify at least one boot class in the spray.servlet.boot-classes config setting.")
        case classes =>
          for (className <- classes) {
            loader.loadClass(className).newInstance()
          }
      }
    }
  }

  def contextDestroyed(e: ServletContextEvent) {
    booted switchOff {
      println("Shutting down spray application ...")
      Spray.system.shutdown()
    }
  }
}