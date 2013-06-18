package spray.io

import akka.io._

import org.specs2.mutable.Specification
import spray.testkit.Specs2PipelineStageTest
import akka.util.ByteString

class BackPressureHandlingSpec extends Specification with Specs2PipelineStageTest {
  val stage = BackPressureHandling(4)

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
      commands.expectMsg(Tcp.SuspendReading)

      events.expectNoMsg()

      // still forward received messages
      connectionActor ! Tcp.Received(data)
      events.expectMsg(Tcp.Received(data))

      connectionActor ! write

      commands.expectMsg(Tcp.ResumeWriting)
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
      commands.expectMsg(Tcp.SuspendReading)
      commands.expectMsg(Tcp.ResumeWriting)

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
      commands.expectMsg(Tcp.SuspendReading)
      commands.expectMsg(Tcp.ResumeWriting)

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
      commands.expectMsg(Tcp.SuspendReading)
      commands.expectMsg(Tcp.ResumeWriting)

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
      commands.expectMsg(Tcp.SuspendReading)
      commands.expectMsg(Tcp.ResumeWriting)

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
    "gracefully handle PeerClosed" in pending
  }
}
