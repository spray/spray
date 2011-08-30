/**
 * Copyright (C) 2009-2011 Scalable Solutions AB <http://scalablesolutions.se>
 */

package akka.dispatch

/**
 * Simple extension of ExecutorBasedEventDrivenDispatcher that makes the `dispatch` and
 * `executeTask` methods accessible to derived classes outside of the akka package hierarchy
 *
 * TODO: remove once akka ticket #1145 is closed (http://www.assembla.com/spaces/akka/tickets/1145)
 */
class ImprovedExecutorBasedEventDrivenDispatcher(
  name: String,
  throughput: Int = Dispatchers.THROUGHPUT,
  throughputDeadlineTime: Int = Dispatchers.THROUGHPUT_DEADLINE_TIME_MILLIS,
  mailboxType: MailboxType = Dispatchers.MAILBOX_TYPE,
  config: ThreadPoolConfig = ThreadPoolConfig()
) extends ExecutorBasedEventDrivenDispatcher(name, throughput, throughputDeadlineTime, mailboxType, config) {

  protected[akka] override def dispatch(invocation: MessageInvocation) {
    super.dispatch(invocation)
  }

  protected[akka] override def executeTask(invocation: TaskInvocation) {
    super.executeTask(invocation)
  }
}
