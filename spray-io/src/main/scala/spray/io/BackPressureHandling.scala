package spray.io

import akka.io._
import scala.collection.immutable.Queue
import akka.io.Tcp.{ CloseCommand, NoAck }
import scala.annotation.tailrec
import akka.util.ByteString

/**
 * Automated back-pressure handling is based on the idea that pressure
 * is created by the consumer but experienced at the producer side. E.g.
 * for http that means that a too big number of incoming requests is the
 * ultimate cause of an experienced bottleneck on the response sending side.
 *
 * The principle of applying back-pressure means that the best way of handling
 * pressure is by handling it at the root cause which means throttling the rate
 * at which work requests are coming in. That's the underlying assumption here:
 * work is generated on the incoming network side. If that's not true, e.g. when
 * the network stream is a truly bi-directional one (e.g. websockets) the strategy
 * presented here won't be optimal.
 *
 * How it works:
 *
 * No pressure:
 *   - forward all incoming data
 *   - send out ''n'' responses with NoAcks
 *   - send one response with Ack
 *   - once that ack was received we know all the former unacknowledged writes
 *     have been successful as well and don't need any further handling
 *
 * Pressure:
 *   - a Write fails, we know now that all former writes were successful, all
 *     latter ones, including the failed one were discarded (but we'll still receive CommandFailed
 *     messages for them as well)
 *   - the incoming side is informed to SuspendReading
 *   - we send ResumeWriting which is queued after all the Writes that will be discarded as well
 *   - once we receive WritingResumed go back to the no pressure mode and retry all of the buffered writes
 *   - we schedule a final write probe which will trigger ResumeReading when no lowWatermark is defined
 *   - once we receive the ack for that probe or the buffer size falls below a lowWatermark after
 *     an acknowledged Write, we ResumeReading
 *
 * Possible improvement:
 *   (see http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io-tcp.html)
 *   - go into Ack based mode for a while after WritingResumed
 */
object BackPressureHandling {
  case class Ack(offset: Int) extends Tcp.Event
  object ResumeReadingNow extends Tcp.Event
  object CanCloseNow extends Tcp.Event
  val ProbeForWriteQueueEmpty = Tcp.Write(ByteString.empty, ResumeReadingNow)
  val ProbeForEndOfWriting = Tcp.Write(ByteString.empty, CanCloseNow)

  def apply(ackRate: Int, lowWatermark: Int = Int.MaxValue): PipelineStage =
    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new DynamicPipelines { effective ⇒
          import context.log

          def initialPipeline =
            writeThrough(new OutQueue(ackRate), isReading = true, closeCommand = None)

          /**
           * In this state all incoming write requests have already been relayed to the connection. There's a buffer
           * of still unacknowledged writes to retry when back-pressure is experienced.
           *
           * Invariant:
           *   * we've not experienced any failed writes
           */
          def writeThrough(out: OutQueue, isReading: Boolean, closeCommand: Option[Tcp.CloseCommand]): Pipelines = new Pipelines {
            def resumeReading(): Unit = {
              commandPL(Tcp.ResumeReading)
              become(writeThrough(out, isReading = true, closeCommand))
            }
            def writeFailed(idx: Int): Unit = {
              out.dequeue(idx - 1)

              // go into buffering mode
              commandPL(Tcp.ResumeWriting)
              become(buffering(out, idx, isReading, closeCommand))
            }
            def isClosing = closeCommand.isDefined

            def commandPipeline = {
              case _: Tcp.Write if isClosing ⇒ log.warning("Can't process more writes when closing. Dropping...")
              case w @ Tcp.Write(data, NoAck(noAck)) ⇒
                if (noAck != null) log.warning(s"BackPressureHandling doesn't support custom NoAcks $noAck")

                commandPL(out.enqueue(w))
              case w @ Tcp.Write(data, ack) ⇒ commandPL(out.enqueue(w, forceAck = true))
              case a @ Tcp.Abort            ⇒ commandPL(a) // always forward abort
              case c: Tcp.CloseCommand if out.queueEmpty ⇒
                commandPL(c)
                become(closed())
              case c: Tcp.CloseCommand ⇒
                if (isClosing) log.warning(s"Ignored duplicate close request when closing. $c")
                else {
                  commandPL(ProbeForEndOfWriting)
                  become(writeThrough(out, isReading, Some(c)))
                }

              case c ⇒ commandPL(c)
            }

            def eventPipeline = {
              case Ack(idx) ⇒
                // dequeue and send out possible user level ack
                out.dequeue(idx).foreach(eventPL)

                if (!isReading && out.queueLength < lowWatermark) resumeReading()

              case Tcp.CommandFailed(Tcp.Write(_, NoAck(seq: Int))) ⇒ writeFailed(seq)
              case Tcp.CommandFailed(Tcp.Write(_, Ack(seq: Int)))   ⇒ writeFailed(seq)
              case Tcp.CommandFailed(ProbeForWriteQueueEmpty)       ⇒ writeFailed(out.nextSequenceNo)
              case Tcp.CommandFailed(ProbeForEndOfWriting) ⇒
                // just our probe failed, this still means the queue is empty and we can close now
                commandPL(closeCommand.get)
                become(closed())
              case ResumeReadingNow ⇒ if (!isReading) resumeReading()
              case CanCloseNow ⇒
                require(isClosing, "Received unexpected CanCloseNow when not closing")
                commandPL(closeCommand.get)
                become(closed())
              case e ⇒ eventPL(e)
            }
          }

          /**
           * The state where writing is suspended and we are waiting for WritingResumed. Reading will be suspended
           * if it currently isn't and if the connection isn't already going to be closed.
           */
          def buffering(out: OutQueue, failedSeq: Int, isReading: Boolean, closeCommand: Option[CloseCommand]): Pipelines = {
            def isClosing = closeCommand.isDefined

            if (!isClosing && isReading) {
              commandPL(Tcp.SuspendReading)
              buffering(out, failedSeq, isReading = false, closeCommand)
            } else new Pipelines {
              def commandPipeline = {
                case w: Tcp.Write ⇒
                  if (isClosing) log.warning("Can't process more writes when closing. Dropping...")
                  else out.enqueue(w)
                case a @ Tcp.Abort ⇒ commandPL(a)
                case c: Tcp.CloseCommand ⇒
                  if (isClosing) log.warning(s"Ignored duplicate close request ($c) when closing.")
                  else {
                    // we can resume reading now (even if we don't expect any more to come in)
                    // because by definition more data read can't lead to more traffic on the
                    // writing side once the writing side was closed
                    if (!isReading) commandPL(Tcp.ResumeReading)
                    become(buffering(out, failedSeq, isReading = true, Some(c)))
                  }
                case c ⇒ commandPL(c)
              }
              def eventPipeline = {
                case Tcp.WritingResumed ⇒
                  // TODO: we are rebuilding the complete queue here to be sure all
                  // the side-effects have been applied as well
                  // This could be improved by reusing the internal data structures and
                  // just executing the side-effects

                  become(writeThrough(new OutQueue(ackRate, out.headSequenceNo), isReading = isReading, closeCommand = None))
                  out.queue.foreach(effective.commandPipeline) // commandPipeline is already in writeThrough state

                  // we run one special probe writing request to make sure we will ResumeReading when the queue is empty
                  if (!isClosing) commandPL(ProbeForWriteQueueEmpty)
                  // otherwise, if we are closing we replay the close as well
                  else effective.commandPipeline(closeCommand.get)

                case Tcp.CommandFailed(_: Tcp.Write)  ⇒ // Drop. This is expected.
                case Ack(seq) if seq == failedSeq - 1 ⇒
                // Ignore. This is expected since if the last successful write was an
                // ack'd one and the next one fails (because of the ack'd one still being in the queue)
                // the CommandFailed will be received before the Ack
                case Ack(seq)                         ⇒ log.warning(s"Unexpected Ack($seq) in buffering mode. length: ${out.queueLength} head: ${out.headSequenceNo}")
                case e                                ⇒ eventPL(e)
              }
            }
          }

          def closed(): Pipelines = new Pipelines {
            def commandPipeline = {
              case c @ (_: Tcp.Write | _: Tcp.CloseCommand) ⇒ log.warning(s"Connection is already closed, dropping command $c")
              case c                                        ⇒ commandPL(c)
            }
            def eventPipeline = {
              case e ⇒ eventPL(e)
            }
          }
        }
    }

  /** A mutable queue of outgoing write requests */
  class OutQueue(ackRate: Int, _initialSequenceNo: Int = 0) {
    // the current number of unacked Writes
    private[this] var unacked = 0
    // our buffer of sent but unacked Writes
    private[this] var buffer = Queue.empty[Tcp.Write]
    // the sequence number of the first Write in the buffer
    private[this] var firstSequenceNo = _initialSequenceNo

    private[this] var length = 0

    def enqueue(w: Tcp.Write, forceAck: Boolean = false): Tcp.Write = {
      val seq = firstSequenceNo + length // is that efficient, otherwise maintain counter
      buffer = buffer.enqueue(w)
      length += 1

      val shouldAck = forceAck || unacked >= ackRate - 1

      // reset the counter whenever we Ack
      if (shouldAck) unacked = 0
      else unacked += 1

      val ack = if (shouldAck) Ack(seq) else NoAck(seq)
      Tcp.Write(w.data, ack)
    }
    @tailrec final def dequeue(upToSeq: Int): Option[Event] =
      if (firstSequenceNo < upToSeq) {
        firstSequenceNo += 1
        buffer = buffer.tail
        length -= 1
        dequeue(upToSeq)
      } else if (firstSequenceNo == upToSeq) { // the last one may contain an ack to send
        firstSequenceNo += 1
        val front = buffer.front
        buffer = buffer.tail
        length -= 1

        if (front.wantsAck) Some(front.ack)
        else None
      } else if (firstSequenceNo - 1 == upToSeq) None // first one failed
      else throw new IllegalStateException(s"Shouldn't get here, $firstSequenceNo > $upToSeq")

    def queue: Queue[Tcp.Write] = buffer
    def queueEmpty: Boolean = length == 0
    def queueLength: Int = length
    def headSequenceNo = firstSequenceNo
    def nextSequenceNo = firstSequenceNo + length
  }
}
