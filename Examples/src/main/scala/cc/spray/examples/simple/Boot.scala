package cc.spray.examples.simple

import akka.actor.SupervisorFactory
import akka.config.Supervision._
import akka.actor.Actor._
import cc.spray._
import utils.ActorHelpers._

class Boot extends Service {
  SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      Supervise(actorOf[RootService], Permanent) ::
      Nil
    )
  ).newInstance.start
  
  actor[RootService] ! Attach(HttpService(sampleService))
}