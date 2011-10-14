package cc.spray.examples.client

import akka.config.Supervision._
import akka.actor.Supervisor
import akka.actor.Actor._
import cc.spray._

class Boot {
  
  val mainModule = new SimpleService {
    // bake your module cake here
  }
  
  val httpService = actorOf(new HttpService(mainModule.simpleService))
  val httpService2 = actorOf(new HttpService(mainModule.secondService))
  val rootService = actorOf(new RootService(httpService, httpService2))

  // start and supervise the created actors
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(
        Supervise(httpService, Permanent),
        Supervise(httpService2, Permanent),
        Supervise(rootService, Permanent)
      )
    )
  )
}