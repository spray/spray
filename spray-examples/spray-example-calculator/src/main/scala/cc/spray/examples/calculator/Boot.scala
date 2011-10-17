package cc.spray
package examples.calculator

import akka.config.Supervision._
import akka.actor.Supervisor
import akka.actor.Actor._

class Boot {
  
  val mainModule = new CalculatorService {
    // bake your module cake here
  }
  
  val httpService = actorOf(new HttpService(mainModule.calculatorService))
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