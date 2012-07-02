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

package cc.spray.servlet

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

      def missingMemberError(className: String, member: String): PartialFunction[Throwable, Unit] = {
        case e@ (_: NoSuchMethodException | _: ClassCastException) =>
          servletContext.log("Configured boot class " + className + " does not define a " + member, e)
      }

      def notFoundError(className: String): PartialFunction[Throwable, Unit] = {
        case e: ClassNotFoundException =>
          servletContext.log("Configured boot class " + className + " cannot be found", e)
      }

      try {
        val classLoader = ActorSystem.asInstanceOf[{ def findClassLoader(): ClassLoader }].findClassLoader()
        val config = ConfigFactory.load(classLoader)
        val settings = new ConnectorSettings(config)
        servletContext.setAttribute(Initializer.SettingsAttrName, settings)
        try {
          val bootClass = classLoader.loadClass(settings.BootClass)
          val constructor = bootClass.getConstructor()
          try {
            val boot = constructor.newInstance()
            val system = boot.asInstanceOf[{ def system: ActorSystem }].system
            actorSystem = Some(system)
            servletContext.setAttribute(Initializer.SystemAttrName, system)
            try {
              val serviceActor = boot.asInstanceOf[{ def serviceActor: ActorRef }].serviceActor
              servletContext.setAttribute(Initializer.ServiceActorAttrName, serviceActor)
            } catch missingMemberError(settings.BootClass, "`def serviceActor: ActorRef` member")
          } catch missingMemberError(settings.BootClass, "`def system: ActorSystem` member")
        } catch missingMemberError(settings.BootClass, "default constructor") orElse notFoundError(settings.BootClass)
      } catch { case NonFatal(e) => servletContext.log(e.getMessage, e) }
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
