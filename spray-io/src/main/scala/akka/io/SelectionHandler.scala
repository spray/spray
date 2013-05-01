/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.io

import java.util.{ Iterator ⇒ JIterator }
import java.lang.Runnable
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{ SelectableChannel, SelectionKey, CancelledKeyException }
import java.nio.channels.SelectionKey._
import java.util.concurrent.atomic.AtomicBoolean
import com.typesafe.config.Config
import scala.annotation.tailrec
import scala.util.control.NonFatal
import akka.dispatch.MessageDispatcher
import akka.event.LoggingAdapter
import akka.io.IO.HasFailureMessage
import akka.actor._

abstract class SelectionHandlerSettings(config: Config) {
  import config._

  val MaxChannels: Int = getString("max-channels") match {
    case "unlimited" ⇒ -1
    case _           ⇒ getInt("max-channels")
  }
  val SelectorAssociationRetries: Int = getInt("selector-association-retries")
  val SelectorDispatcher: String = getString("selector-dispatcher")
  val WorkerDispatcher: String = getString("worker-dispatcher")
  val TraceLogging: Boolean = getBoolean("trace-logging")

  require(MaxChannels == -1 || MaxChannels > 0, "max-channels must be > 0 or 'unlimited'")
  require(SelectorAssociationRetries >= 0, "selector-association-retries must be >= 0")

  def MaxChannelsPerSelector: Int
}

private[io] trait ChannelRegistry {
  def register(channel: SelectableChannel, initialOps: Int)(implicit channelActor: ActorRef)
}

private[io] trait ChannelRegistration {
  def enableInterest(op: Int)
  def disableInterest(op: Int)
}

private[io] object SelectionHandler {

  case class WorkerForCommand(apiCommand: HasFailureMessage, commander: ActorRef, childProps: ChannelRegistry ⇒ Props)

  case class Retry(command: WorkerForCommand, retriesLeft: Int) { require(retriesLeft >= 0) }

  case object ChannelConnectable
  case object ChannelAcceptable
  case object ChannelReadable
  case object ChannelWritable

  private class ChannelRegistryImpl(dispatcher: MessageDispatcher, log: LoggingAdapter) extends ChannelRegistry {
    private[this] val selector = SelectorProvider.provider.openSelector
    private[this] val wakeUp = new AtomicBoolean(false)

    final val OP_READ_AND_WRITE = OP_READ | OP_WRITE // compile-time constant

    private[this] val select = new Task {
      def tryRun(): Unit = {
        wakeUp.set(false) // reset early, worst-case we do a double-wakeup, but it's supposed to be idempotent so it's just an extra syscall
        if (selector.select() > 0) { // this assumes select return value == selectedKeys.size
          val keys = selector.selectedKeys
          val iterator = keys.iterator()
          while (iterator.hasNext) {
            val key = iterator.next()
            if (key.isValid) {
              try {
                // cache because the performance implications of calling this on different platforms are not clear
                val readyOps = key.readyOps()
                key.interestOps(key.interestOps & ~readyOps) // prevent immediate reselection by always clearing
                val connection = key.attachment.asInstanceOf[ActorRef]
                readyOps match {
                  case OP_READ                   ⇒ connection ! ChannelReadable
                  case OP_WRITE                  ⇒ connection ! ChannelWritable
                  case OP_READ_AND_WRITE         ⇒ { connection ! ChannelWritable; connection ! ChannelReadable }
                  case x if (x & OP_ACCEPT) > 0  ⇒ connection ! ChannelAcceptable
                  case x if (x & OP_CONNECT) > 0 ⇒ connection ! ChannelConnectable
                  case x                         ⇒ log.warning("Invalid readyOps: [{}]", x)
                }
              } catch {
                case _: CancelledKeyException ⇒
                // can be ignored because this exception is triggered when the key becomes invalid
                // because `channel.close()` in `TcpConnection.postStop` is called from another thread
              }
            }
          }
          keys.clear() // we need to remove the selected keys from the set, otherwise they remain selected
        }
      }

      override def run(): Unit = if (selector.isOpen) super.run()

      override def postRun(): Unit = dispatcher.execute(this) // re-schedule select behind all currently queued tasks
    }

    dispatcher.execute(select) // start selection "loop"

    def register(channel: SelectableChannel, initialOps: Int)(implicit channelActor: ActorRef): Unit =
      execute {
        new Task {
          def tryRun(): Unit = {
            val key = channel.register(selector, initialOps, channelActor)
            channelActor ! new ChannelRegistration {
              def enableInterest(ops: Int): Unit = enableInterestOps(key, ops)
              def disableInterest(ops: Int): Unit = disableInterestOps(key, ops)
            }
          }
        }
      }

    def shutdown(): Unit =
      execute {
        new Task {
          def tryRun(): Unit = {
            // thorough 'close' of the Selector
            @tailrec def closeNextChannel(it: JIterator[SelectionKey]): Unit = if (it.hasNext) {
              try it.next().channel.close() catch { case NonFatal(e) ⇒ log.error(e, "Error closing channel") }
              closeNextChannel(it)
            }
            try closeNextChannel(selector.keys.iterator) finally selector.close()
          }
        }
      }

    // always set the interest keys on the selector thread according to benchmark
    private def enableInterestOps(key: SelectionKey, ops: Int): Unit =
      execute {
        new Task {
          def tryRun(): Unit = {
            val currentOps = key.interestOps
            val newOps = currentOps | ops
            if (newOps != currentOps) key.interestOps(newOps)
          }
        }
      }

    private def disableInterestOps(key: SelectionKey, ops: Int): Unit =
      execute {
        new Task {
          def tryRun(): Unit = {
            val currentOps = key.interestOps
            val newOps = currentOps & ~ops
            if (newOps != currentOps) key.interestOps(newOps)
          }
        }
      }

    private def execute(task: Task): Unit = {
      dispatcher.execute(task)
      if (wakeUp.compareAndSet(false, true)) selector.wakeup() // Avoiding syscall and trade off with LOCK CMPXCHG
    }

    // FIXME: Add possibility to signal failure of task to someone
    private abstract class Task extends Runnable {
      def tryRun()
      def run() {
        try tryRun()
        catch {
          case _: CancelledKeyException ⇒ // ok, can be triggered while setting interest ops
          case NonFatal(e)              ⇒ log.error(e, "Error during selector management task: [{}]", e)
        } finally postRun()
      }
      def postRun(): Unit = ()
    }
  }
}

private[io] class SelectionHandler(settings: SelectionHandlerSettings) extends Actor with ActorLogging {
  import SelectionHandler._
  import settings._

  private[this] val registry = new ChannelRegistryImpl(context.system.dispatchers.lookup(SelectorDispatcher), log)
  private[this] var sequenceNumber = 0
  private[this] var childCount = 0

  def receive: Receive = {
    case cmd: WorkerForCommand   ⇒ spawnChildWithCapacityProtection(cmd, SelectorAssociationRetries)

    case Retry(cmd, retriesLeft) ⇒ spawnChildWithCapacityProtection(cmd, retriesLeft)

    case _: Terminated           ⇒ childCount -= 1
  }

  override def postStop(): Unit = registry.shutdown()

  // we can never recover from failures of a connection or listener child
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def spawnChildWithCapacityProtection(cmd: WorkerForCommand, retriesLeft: Int): Unit = {
    if (TraceLogging) log.debug("Executing [{}]", cmd)
    if (MaxChannelsPerSelector == -1 || childCount < MaxChannelsPerSelector) {
      val newName = sequenceNumber.toString
      sequenceNumber += 1
      childCount += 1
      val child = context.actorOf(props = cmd.childProps(registry).withDispatcher(WorkerDispatcher), name = newName)
      if (MaxChannelsPerSelector > 0) context.watch(child) // we don't need to watch if we aren't limited
    } else {
      if (retriesLeft >= 1) {
        log.warning("Rejecting [{}] with [{}] retries left, retrying...", cmd, retriesLeft)
        context.parent forward Retry(cmd, retriesLeft - 1)
      } else {
        log.warning("Rejecting [{}] with no retries left, aborting...", cmd)
        cmd.commander ! cmd.apiCommand.failureMessage // I can't do it, Captain!
      }
    }
  }
}