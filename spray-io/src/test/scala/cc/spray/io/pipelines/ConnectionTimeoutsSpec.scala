package cc.spray.io.pipelines

import org.specs2.mutable.Specification
import cc.spray.io.test.PipelineStageTest
import cc.spray.io.{IoPeer, IdleTimeout, Event, Command}

class ConnectionTimeoutsSpec extends Specification with PipelineStageTest {
  val fixture = new Fixture(ConnectionTimeouts(50, system.log))

  "The ConnectionTimeouts PipelineStage" should {
    "be transparent to unrelated commands" in {
      val command = new Command {}
      fixture(command).commands === Seq(command)
    }
    "be transparent to unrelated events" in {
      val event = new Event {}
      fixture(event).events === Seq(event)
    }
    "upon a Tick, create a Close command if the idle timeout expired" in {
      fixture(
        Received("Some Message"),
        Sleep("60 ms"),
        TickGenerator.Tick
      ).commands === Seq(IoPeer.Close(IdleTimeout))
    }
    "reset the idle timer on Received events" in {
      fixture(
        Sleep("50 ms"),
        Received("Some Message"),
        TickGenerator.Tick
      ).commands === Seq()
    }
    "reset the idle timer on Send commands" in {
      fixture(
        Sleep("50 ms"),
        Send("Some Message"),
        TickGenerator.Tick
      ).commands.fixSends === Seq(SendString("Some Message"))
    }
  }

  step {
    cleanup()
  }
}
