package cc.spray.io

import org.specs2.Specification
import org.specs2.specification.Step
import akka.actor.{Props, ActorSystem}

class IoWorkerSpec extends Specification { def is =

  sequential^
  "This spec exercises am IoWorker instance against itself"    ^
                                                                      p^
                                                                      Step(start())^
  "simple one-request dialog"                                         ! oneRequestDialog^
                                                                      Step(stop())

  def start() {
    val system = ActorSystem("MySystem")
    val myActor = system.actorOf(Props[IoServerActor], name = "myactor")
    myActor ! 'dfdf
  }

  def stop()

}
