package cc.spray.connectors

import akka.actor.BootableActorLoaderService

import javax.servlet.{ServletContextListener, ServletContextEvent}
import akka.util.AkkaLoader

class Initializer extends ServletContextListener {
  lazy val loader = new AkkaLoader
  
  def contextDestroyed(e: ServletContextEvent) {
    loader.shutdown()
  }
  
  def contextInitialized(e: ServletContextEvent) {
    loader.boot(true, new BootableActorLoaderService {})
  }
}
