package cc.spray.examples.deft

import akka.config.Supervision._
import akka.actor.Supervisor
import akka.actor.Actor._
import cc.spray._
import utils.ActorHelpers._

class Boot {
  
  val mainModule = new HelloService {
    // bake your module cake here
  }
  
  // start the root service actor (and any service actors you want to specify supervision details for)
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(
        Supervise(actorOf[RootService], Permanent)
      )
    )
  )
  
  // attach an HttpService (which is also an actor)
  // the root service automatically starts the HttpService if it is unstarted
  actor[RootService] ! Attach(HttpService(mainModule.helloService))
}