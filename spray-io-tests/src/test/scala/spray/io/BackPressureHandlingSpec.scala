package spray.io

import akka.io._

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import spray.testkit.Specs2PipelineStageTest
import akka.util.ByteString

class BackPressureHandlingSpec extends Specification with Specs2PipelineStageTest {
  val stage = BackPressureHandling(4)

  override lazy val config = ConfigFactory.parseString("akka.test.single-expect-default = 100 ms")

  val data = ByteString(0, 1, 2)
  val write = Tcp.Write(data)
  def noAck(id: Int) = Tcp.NoAck(id)
  def ack(id: Int) = BackPressureHandling.Ack(id)
  def NoAckedWrite(id: Int) = Tcp.Write(data, noAck(id))
  def AckedWrite(id: Int) = Tcp.Write(data, ack(id))
  /** A custom ack coming from the outside */
  object MyAck extends Tcp.Event

  "BackPressureHandling" should {
    "writethrough as long as there's no backpressure" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(2))

      connectionActor ! write
      commands.expectMsg(AckedWrite(3))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(4))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(5))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(6))

      connectionActor ! write
      commands.expectMsg(AckedWrite(7))

      events.expectNoMsg()
    }
    "properly handle incoming Acked requests" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! Tcp.Write(data, MyAck)
      commands.expectMsg(AckedWrite(1))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(2))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(3))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(4))

      commands.expectNoMsg()
      events.expectNoMsg()
      connectionActor ! ack(1)
      events.expectMsg(MyAck)
    }
    "buffer on backpressure after NoAcked send" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(1))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      events.expectNoMsg()

      // still forward received messages
      connectionActor ! Tcp.Received(data)
      events.expectMsg(Tcp.Received(data))

      connectionActor ! write

      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(NoAckedWrite(1))
      commands.expectMsg(NoAckedWrite(2))
      commands.expectMsg(BackPressureHandling.ProbeForWriteQueueEmpty)

      connectionActor ! BackPressureHandling.ResumeReadingNow
      commands.expectMsg(Tcp.ResumeReading)

      events.expectNoMsg()
    }
    "buffer on backpressure after several NoAcked send" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(2))

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(1))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(2))
      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(NoAckedWrite(1))
      commands.expectMsg(NoAckedWrite(2))
      commands.expectMsg(BackPressureHandling.ProbeForWriteQueueEmpty)

      connectionActor ! BackPressureHandling.ResumeReadingNow
      commands.expectMsg(Tcp.ResumeReading)

      events.expectNoMsg()
    }
    "buffer on backpressure after several Acked send fails" in new Fixture(stage) {
      connectionActor ! Tcp.Write(data, MyAck)
      commands.expectMsg(AckedWrite(0))

      commands.expectNoMsg()
      connectionActor ! Tcp.CommandFailed(AckedWrite(0))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(AckedWrite(0))

      events.expectNoMsg()

      connectionActor ! ack(0)
      events.expectMsg(MyAck)
    }
    "buffer on backpressure when first unacked write fails" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      commands.expectNoMsg()
      connectionActor ! Tcp.CommandFailed(NoAckedWrite(0))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(NoAckedWrite(0))

      events.expectNoMsg()
    }
    "buffer on backpressure and resume reading only after having reached lowWatermark" in new Fixture(
      BackPressureHandling(ackRate = 4, lowWatermark = 2)) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(2))

      connectionActor ! write
      commands.expectMsg(AckedWrite(3))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(4))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(5))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(6))

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(1))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(2))
      connectionActor ! Tcp.CommandFailed(NoAckedWrite(3))
      connectionActor ! Tcp.CommandFailed(AckedWrite(4))
      connectionActor ! Tcp.CommandFailed(NoAckedWrite(5))
      connectionActor ! Tcp.CommandFailed(NoAckedWrite(6))
      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(NoAckedWrite(1))
      commands.expectMsg(NoAckedWrite(2))
      commands.expectMsg(NoAckedWrite(3))
      commands.expectMsg(AckedWrite(4))
      commands.expectMsg(NoAckedWrite(5))
      commands.expectMsg(NoAckedWrite(6))
      commands.expectMsg(BackPressureHandling.ProbeForWriteQueueEmpty)

      connectionActor ! ack(4)
      connectionActor ! Tcp.ResumeReading

      events.expectNoMsg()
    }

    "Close immediately if queue is empty" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(2))

      connectionActor ! write
      commands.expectMsg(AckedWrite(3))

      // queue can be emptied afterwards
      connectionActor ! ack(3)
      commands.expectNoMsg()

      // close directly
      connectionActor ! Tcp.ConfirmedClose
      commands.expectMsg(Tcp.ConfirmedClose)
    }
    "gracefully handle Close messages without backpressure" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! Tcp.ConfirmedClose
      // close isn't send directly but instead a probe is queued to notify us that
      // all former writes did complete successfully
      commands.expectMsg(BackPressureHandling.ProbeForEndOfWriting)

      commands.expectNoMsg()

      connectionActor ! BackPressureHandling.CanCloseNow
      commands.expectMsg(Tcp.ConfirmedClose)
    }
    "gracefully handle Close messages with backpressure (before experienced)" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! Tcp.ConfirmedClose
      commands.expectMsg(BackPressureHandling.ProbeForEndOfWriting)

      commands.expectNoMsg()

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(1))
      // probe is discarded as well, so we have to make sure it is rescheduled later
      connectionActor ! Tcp.CommandFailed(BackPressureHandling.ProbeForEndOfWriting)
      commands.expectMsg(Tcp.ResumeWriting)

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(NoAckedWrite(1))
      commands.expectMsg(BackPressureHandling.ProbeForEndOfWriting)

      connectionActor ! BackPressureHandling.CanCloseNow
      commands.expectMsg(Tcp.ConfirmedClose)
    }
    "gracefully handle Close messages with backpressure (in buffering mode)" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      commands.expectNoMsg()

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(1))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      connectionActor ! Tcp.ConfirmedClose
      // we ResumeReading instantly (since no writes are expected anyways) but defer
      // actually closing to after the point where all writes have been sent
      commands.expectMsg(Tcp.ResumeReading)
      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(NoAckedWrite(1))
      commands.expectMsg(BackPressureHandling.ProbeForEndOfWriting)

      connectionActor ! BackPressureHandling.CanCloseNow
      commands.expectMsg(Tcp.ConfirmedClose)
    }
    "abort instantly" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(1))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      connectionActor ! Tcp.Abort
      // don't defer aborts at all
      commands.expectMsg(Tcp.Abort)
    }

    "a SuspendReading probe fails to be handled" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      commands.expectNoMsg()
      connectionActor ! Tcp.CommandFailed(NoAckedWrite(0))
      commands.expectMsg(Tcp.ResumeWriting)
      commands.expectMsg(Tcp.SuspendReading)

      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(NoAckedWrite(0))
      commands.expectMsg(BackPressureHandling.ProbeForWriteQueueEmpty)

      connectionActor ! Tcp.CommandFailed(BackPressureHandling.ProbeForWriteQueueEmpty)
      commands.expectMsg(Tcp.ResumeWriting)

      commands.expectNoMsg()

      connectionActor ! Tcp.WritingResumed
      commands.expectMsg(BackPressureHandling.ProbeForWriteQueueEmpty)

      events.expectNoMsg()
    }
    "a Close probe fails to be handled" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! Tcp.ConfirmedClose
      commands.expectMsg(BackPressureHandling.ProbeForEndOfWriting)

      commands.expectNoMsg()

      connectionActor ! Tcp.CommandFailed(BackPressureHandling.ProbeForEndOfWriting)
      // if the probe is the thing that's failing we still know that we now the last thing we want to do is closing
      // so we do just this
      commands.expectMsg(Tcp.ConfirmedClose)

      events.expectNoMsg()
    }
    "don't report Ack on the pipeline when Write after successful Ack fails" in new Fixture(stage) {
      connectionActor ! write
      commands.expectMsg(NoAckedWrite(0))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(1))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(2))

      connectionActor ! write
      commands.expectMsg(AckedWrite(3))

      connectionActor ! write
      commands.expectMsg(NoAckedWrite(4))

      connectionActor ! Tcp.CommandFailed(NoAckedWrite(4))
      connectionActor ! ack(3)

      events.expectNoMsg()
    }
    // FIXME: these cases are not yet handled
    "what happens if WriteFile fails" in pending
  }
}
