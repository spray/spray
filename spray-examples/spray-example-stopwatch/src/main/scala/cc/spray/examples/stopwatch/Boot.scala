package cc.spray.examples.stopwatch

import akka.config.Supervision._
import akka.actor.Supervisor
import akka.actor.Actor._
import cc.spray._

class Boot {
  
  val mainModule = new StopWatchService {
    // bake your module cake here
  }
  
  val httpService = actorOf(HttpService(mainModule.stopWatchService))
  val rootService = actorOf(RootService(httpService))

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