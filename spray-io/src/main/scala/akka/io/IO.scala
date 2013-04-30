/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.io

import akka.actor._
import akka.routing.RandomRouter
import akka.io.SelectionHandler.WorkerForCommand

object IO {

  trait Extension extends akka.actor.Extension {
    def manager: ActorRef
  }

  def apply[T <: Extension](key: ExtensionKey[T])(implicit system: ActorSystem): ActorRef = key(system).manager

  // What is this? It's public API so I think it deserves a mention
  trait HasFailureMessage {
    def failureMessage: Any
  }

  abstract class SelectorBasedManager(selectorSettings: SelectionHandlerSettings, nrOfSelectors: Int) extends Actor {

    val selectorPool = context.actorOf(
      props = Props(new SelectionHandler(selectorSettings)).withRouter(RandomRouter(nrOfSelectors)),
      name = "selectors")

    def workerForCommandHandler(pf: PartialFunction[HasFailureMessage, ChannelRegistry ⇒ Props]): Receive = {
      case cmd: HasFailureMessage if pf.isDefinedAt(cmd) ⇒ selectorPool ! WorkerForCommand(cmd, sender, pf(cmd))
    }
  }

}