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

import akka.util.{NonFatal, Switch}
import akka.actor.{ActorRef, ActorSystem}
import javax.servlet.{ServletContextListener, ServletContextEvent}
import com.typesafe.config.ConfigFactory


class Initializer extends ServletContextListener {
  private val booted = new Switch(false)
  @volatile private[this] var actorSystem: Option[ActorSystem] = None

  def contextInitialized(ev: ServletContextEvent) {
    booted switchOn {
      println("Starting spray application ...")
      val servletContext = ev.getServletContext

      try {
        val classLoader = ActorSystem.asInstanceOf[{ def findClassLoader(): ClassLoader }].findClassLoader()
        val config = ConfigFactory.load(classLoader)
        val settings = new ConnectorSettings(config)
        servletContext.setAttribute(Initializer.SettingsAttrName, settings)
        def configuredBootClass = "Configured boot class " + settings.BootClass
        try {
          val bootClass = classLoader.loadClass(settings.BootClass)
          val constructor = bootClass.getConstructor()
          try {
            val boot = constructor.newInstance()
            val webBoot = boot.asInstanceOf[WebBoot]
            actorSystem = Some(webBoot.system)
            servletContext.setAttribute(Initializer.SystemAttrName, actorSystem.get)
            servletContext.setAttribute(Initializer.ServiceActorAttrName, webBoot.serviceActor)
          } catch {
            case e: ClassCastException =>
              servletContext.log(configuredBootClass + " does not implement spray.servlet.WebBoot", e)
          }
        } catch {
          case e: ClassNotFoundException =>
            servletContext.log(configuredBootClass + " cannot be found", e)
          case e: NoSuchMethodException=>
            servletContext.log(configuredBootClass + " does not define a default constructor", e)
        }
      } catch {
        case NonFatal(e) => servletContext.log(e.getMessage, e)
      }
    }
  }

  def contextDestroyed(e: ServletContextEvent) {
    booted switchOff {
      println("Shutting down spray application ...")
      actorSystem.foreach(_.shutdown())
    }
  }
}

object Initializer {
  val SystemAttrName = "spray.servlet.system"
  val ServiceActorAttrName = "spray.servlet.serviceActor"
  val SettingsAttrName = "spray.servlet.settings"
}
