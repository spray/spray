package akka.io

import akka.actor.{ OneForOneStrategy, SupervisorStrategy }
import akka.actor.SupervisorStrategy._

object ExtraStrategies {
  final val stoppingStrategy: SupervisorStrategy = {
    import SupervisorStrategy._
    def stoppingDecider: Decider = {
      case _: Exception ⇒ Stop
    }
    OneForOneStrategy()(stoppingDecider)
  }
}
