package spray.util

import scala.collection.immutable.Queue
import akka.actor.{ ActorRef, Actor }

/**
 * A simplistic Stash implementation that doesn't need a special mailbox and doesn't
 * offer any ordering as the standard Stash which sorts unstashed messages to the front
 * of the mailbox.
 *
 * It also doesn't provide any reasonable Actor restart behavior.
 */
private[spray] trait SimpleStash { _: Actor ⇒
  private[this] var buffered = Queue.empty[(ActorRef, Any)]
  def stash(x: Any): Unit = buffered = buffered.enqueue(sender() -> x)
  def unstashAll() = {
    buffered foreach { case (sender, msg) ⇒ self.tell(msg, sender) }
    buffered = Queue.empty
  }
}
