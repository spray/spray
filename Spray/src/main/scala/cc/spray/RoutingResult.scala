package cc.spray

sealed trait RoutingResult
object Handled extends RoutingResult
object NotHandled extends RoutingResult