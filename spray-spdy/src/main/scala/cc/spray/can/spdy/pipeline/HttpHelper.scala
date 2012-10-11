package cc.spray.can.spdy.pipeline

import cc.spray.io.Event
import cc.spray.can.HttpEvent

object HttpHelper {
  /**
   * Unwraps HttpEvent to send to the messageHandler
   */
  def unwrapHttpEvent(e: Event): Any = e match {
    case HttpEvent(e) => e
    case x => x
  }
}
