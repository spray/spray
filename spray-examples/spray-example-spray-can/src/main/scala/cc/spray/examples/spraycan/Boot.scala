package cc.spray.examples.spraycan

import akka.config.Supervision._
import akka.actor.{Supervisor, Actor}
import cc.spray.{RootService, HttpService}
import cc.spray.can.HttpServer
import Actor._

object Boot extends App {

  val mainModule = new HelloService {
    // bake your module cake here
  }

  val httpService    = actorOf(HttpService(mainModule.helloService))
  val rootService    = actorOf(RootService(httpService))
  val sprayCanServer = actorOf(new HttpServer())

  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(
        Supervise(httpService, Permanent),
        Supervise(rootService, Permanent),
        Supervise(sprayCanServer, Permanent)
      )
    )
  )
}