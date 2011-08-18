package cc.spray.examples.calculator

import akka.config.Supervision._
import akka.actor.Supervisor
import akka.actor.Actor._
import cc.spray._

class Boot {
  
  val mainModule = new CalculatorService {
    // bake your module cake here
  }
  
  val httpService = actorOf(HttpService(mainModule.calculatorService))
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