package cc.spray

sealed trait RoutingResult {
  def handledOr(f: => RoutingResult): RoutingResult 
  def unhandledOr(f: => RoutingResult): RoutingResult
  def onHandled(f: => Unit): RoutingResult
  def onUnhandled(f: => Unit): RoutingResult
}

object Handled extends RoutingResult {
  def handledOr(f: => RoutingResult) = this
  def unhandledOr(f: => RoutingResult) = f
  def onHandled(f: => Unit) = { f; this }
  def onUnhandled(f: => Unit) = this
}

object Unhandled extends RoutingResult {
  def handledOr(f: => RoutingResult) = f
  def unhandledOr(f: => RoutingResult) = this
  def onHandled(f: => Unit) = this 
  def onUnhandled(f: => Unit) = { f; this }
}