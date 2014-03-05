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

import javax.servlet.{ ServletContext, ServletContextListener, ServletContextEvent }
import com.typesafe.config.ConfigFactory
import scala.language.reflectiveCalls
import scala.util.control.NonFatal
import akka.util.Switch
import akka.actor.ActorSystem
import spray.util._
import spray.http.Uri

class Initializer extends ServletContextListener {
  private val booted = new Switch(false)
  @volatile private[this] var actorSystem: Option[ActorSystem] = None

  def contextInitialized(ev: ServletContextEvent): Unit = {
    booted switchOn {
      val ctx = ev.getServletContext
      ctx.log("Starting spray application ...")

      try {
        val classLoader = ActorSystem.asInstanceOf[{ def findClassLoader(): ClassLoader }].findClassLoader()
        val config = ConfigFactory.load(classLoader)
        val settings0 = ConnectorSettings(config)
        val settings =
          if (settings0.rootPath == Uri.Path("AUTO")) {
            ctx.log(s"Automatically setting spray.servlet.root-path to '${ctx.getContextPath}'")
            settings0.copy(rootPath = Uri.Path(ctx.getContextPath))
          } else settings0

        ctx.setAttribute(Initializer.SettingsAttrName, settings)
        def errorMsg(msg: String) = "Configured boot class " + settings.bootClass + ' ' + msg
        try {
          val bootClass = classLoader.loadClass(settings.bootClass)
          val boot =
            try {
              val constructor = bootClass.getConstructor(classOf[ServletContext])
              constructor.newInstance(ctx)
            } catch {
              case e: NoSuchMethodException ⇒
                val constructor = bootClass.getConstructor()
                constructor.newInstance()
            }
          try {
            val webBoot = boot.asInstanceOf[WebBoot]
            actorSystem = Some(webBoot.system)
            ctx.setAttribute(Initializer.SystemAttrName, actorSystem.get)
            ctx.setAttribute(Initializer.ServiceActorAttrName, webBoot.serviceActor)
          } catch {
            case e: ClassCastException ⇒ ctx.log(errorMsg("does not implement spray.servlet.WebBoot"), e)
          }
        } catch {
          case e: ClassNotFoundException ⇒ ctx.log(errorMsg("cannot be found"), e)
          case e: NoSuchMethodException ⇒ ctx.log(errorMsg("neither defines a constructor with a single " +
            "`javax.servlet.ServletContext` parameter nor a default constructor"), e)
        }
      } catch {
        case NonFatal(e) ⇒ ctx.log(e.getMessage.nullAsEmpty, e)
      }
    }
  }

  def contextDestroyed(e: ServletContextEvent): Unit = {
    booted switchOff {
      println("Shutting down spray application ...")
      actorSystem.foreach(_.shutdown())
      actorSystem.foreach(_.awaitTermination())
    }
  }
}

object Initializer {
  val SystemAttrName = "spray.servlet.system"
  val ServiceActorAttrName = "spray.servlet.serviceActor"
  val SettingsAttrName = "spray.servlet.settings"
}
