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

package spray.servlet

import javax.servlet.{ ServletContext, ServletContextListener, ServletContextEvent }
import com.typesafe.config.ConfigFactory
import scala.language.reflectiveCalls
import scala.util.control.NonFatal
import akka.util.Switch
import akka.actor.ActorSystem
import spray.util._

class Initializer extends ServletContextListener {
  private val booted = new Switch(false)
  @volatile private[this] var actorSystem: Option[ActorSystem] = None

  def contextInitialized(ev: ServletContextEvent): Unit = {
    booted switchOn {
      println("Starting spray application ...")
      val servletContext = ev.getServletContext

      try {
        val classLoader = ActorSystem.asInstanceOf[{ def findClassLoader(): ClassLoader }].findClassLoader()
        val config = ConfigFactory.load(classLoader)
        val settings = ConnectorSettings(config getConfig "spray.servlet")
        servletContext.setAttribute(Initializer.SettingsAttrName, settings)
        def errorMsg(msg: String) = "Configured boot class " + settings.bootClass + ' ' + msg
        try {
          val bootClass = classLoader.loadClass(settings.bootClass)
          val boot =
            try {
              val constructor = bootClass.getConstructor(classOf[ServletContext])
              constructor.newInstance(servletContext)
            } catch {
              case e: NoSuchMethodException ⇒
                val constructor = bootClass.getConstructor()
                constructor.newInstance()
            }
          try {
            val webBoot = boot.asInstanceOf[WebBoot]
            actorSystem = Some(webBoot.system)
            servletContext.setAttribute(Initializer.SystemAttrName, actorSystem.get)
            servletContext.setAttribute(Initializer.ServiceActorAttrName, webBoot.serviceActor)
          } catch {
            case e: ClassCastException ⇒ servletContext.log(errorMsg("does not implement spray.servlet.WebBoot"), e)
          }
        } catch {
          case e: ClassNotFoundException ⇒ servletContext.log(errorMsg("cannot be found"), e)
          case e: NoSuchMethodException ⇒ servletContext.log(errorMsg("neither defines a constructor with a single " +
            "`javax.servlet.ServletContext` parameter nor a default constructor"), e)
        }
      } catch {
        case NonFatal(e) ⇒ servletContext.log(e.getMessage.nullAsEmpty, e)
      }
    }
  }

  def contextDestroyed(e: ServletContextEvent): Unit = {
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
