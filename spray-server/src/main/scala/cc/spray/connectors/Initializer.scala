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

package cc.spray.connectors

import akka.util.Switch
import cc.spray.SprayServletSettings
import akka.config.ConfigurationException
import akka.actor.ActorSystem
import javax.servlet.{ServletContextListener, ServletContextEvent}

class Initializer extends ServletContextListener {
  private val booted = new Switch(false)
  private lazy val system = ActorSystem("servlet")

  def contextInitialized(e: ServletContextEvent) {
    booted switchOn {
      println("Starting spray application ...")
      val ctx = e.getServletContext
      val loader = getClass.getClassLoader

      Thread.currentThread.setContextClassLoader(loader)

      ctx.setAttribute(Initializer.SystemAttrName, system)

      SprayServletSettings.BootClasses match {
        case Nil =>
          val e = new ConfigurationException("No boot classes configured. Please specify at least one boot class " +
            "in the spray.servlet.boot-classes config setting.")
          ctx.log(e.getMessage, e)

        case classes => {
          for (className <- classes) {
            try {
              loader
                .loadClass(className)
                .getConstructor(classOf[ActorSystem])
                .newInstance(system)
            } catch {
              case e: ClassNotFoundException =>
                ctx.log("Configured boot class " + className + " cannot be found", e)
              case e: NoSuchMethodException =>
                ctx.log("Configured boot class " + className + " does not define required constructor " +
                  "with one parameter of type `akka.actor.ActorSystem`", e)
              case e: Exception =>
                ctx.log("Could not create instance of boot class " + className, e)
            }
          }
        }
      }
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