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
import akka.util.Switch
import cc.spray.SprayServletSettings
import akka.config.ConfigurationException
import akka.actor.ActorSystem

class Initializer extends ServletContextListener {
  private val booted = new Switch(false)
  private lazy val system = ActorSystem("servlet")

  def contextInitialized(e: ServletContextEvent) {
    booted switchOn {
      println("Starting spray application ...")
      val loader = getClass.getClassLoader
      Thread.currentThread.setContextClassLoader(loader)

      e.getServletContext.setAttribute(Initializer.SystemAttrName, system)

      SprayServletSettings.BootClasses match {
        case Nil =>
          throw new ConfigurationException("No boot classes configured. " +
              "Please specify at least one boot class in the spray.servlet.boot-classes config setting.")
        case classes =>
          for (className <- classes) {
            systemConstructor(loader, className).newInstance(system)
          }
      }
    }
  }

  private def systemConstructor(loader: ClassLoader, className: String) = {
    try {
      loader.loadClass(className).getConstructor(classOf[ActorSystem])
    } catch {
      case e: ClassNotFoundException =>
        throw new ConfigurationException("Configured boot class " + className + " cannot be found", e)
      case e: NoSuchMethodException =>
        throw new ConfigurationException("Configured boot class " + className +
          " does not define required constructor with one parameter of type `akka.actor.ActorSystem`", e)
    }
  }

  def contextDestroyed(e: ServletContextEvent) {
    booted switchOff {
      println("Shutting down spray application ...")
      system.shutdown()
    }
  }
}

object Initializer {
  private[connectors] val SystemAttrName = "spray.servlet.system"
}