/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.can

import akka.dispatch._
import java.nio.channels.Selector

/**
 * An ExecutorBasedEventDrivenDispatcher that wakes up a given NIO selector upon arrival of a new message or task.
 */
private[can] class SelectorWakingDispatcher(threadName: String, selector: Selector)
        extends ImprovedExecutorBasedEventDrivenDispatcher(
  name = threadName,
  throughput = -1,
  throughputDeadlineTime = -1,
  mailboxType = UnboundedMailbox(),
  config = ThreadBasedDispatcher.oneThread
) {

  override def dispatch(invocation: MessageInvocation) {
    super.dispatch(invocation)
    if (invocation.message != Select) selector.wakeup()
  }

  override def executeTask(invocation: TaskInvocation) {
    super.executeTask(invocation)
    selector.wakeup()
  }
}