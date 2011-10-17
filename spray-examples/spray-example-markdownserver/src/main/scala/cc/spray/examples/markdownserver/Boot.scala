package cc.spray
package examples.markdownserver

import akka.config.Supervision._
import akka.actor.Supervisor
import akka.actor.Actor._
import cc.spray._

class Boot {
  
  val mainModule = new MarkdownService {
    // bake your module cake here
  }
  
  val httpService = actorOf(new HttpService(mainModule.markdownService))
  val rootService = actorOf(new RootService(httpService))

  // start and supervise the created actors
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(
        Supervise(httpService, Permanent),
        Supervise(rootService, Permanent)
      )
    )
  )
}