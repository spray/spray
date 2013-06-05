package akka.spray

import akka.event.LoggingAdapter

object NoLogging extends LoggingAdapter {
  def isErrorEnabled = false
  def isWarningEnabled = false
  def isInfoEnabled = false
  def isDebugEnabled = false

  protected def notifyError(message: String) {}
  protected def notifyError(cause: Throwable, message: String) {}
  protected def notifyWarning(message: String) {}
  protected def notifyInfo(message: String) {}
  protected def notifyDebug(message: String) {}
}
