package cc.spray.io.test

import collection.mutable.ListBuffer
import cc.spray.io._
import java.nio.ByteBuffer
import akka.util.Duration
import cc.spray.util._
import akka.actor.{ActorContext, ActorSystem}
import java.net.InetSocketAddress

trait PipelineStageTest {
  implicit val system = ActorSystem()

  val dummyHandle = new Handle {
    def key = throw new UnsupportedOperationException
    def handler = throw new UnsupportedOperationException
    val address = new InetSocketAddress("example.com", 8080)
    def commander = throw new UnsupportedOperationException
  }

  class Fixture(stage: PipelineStage) {
    val context = new PipelineContext {
      def handle = dummyHandle
      def connectionActorContext = getConnectionActorContext
    }
    def getConnectionActorContext: ActorContext = throw new UnsupportedOperationException
    def apply(cmdsAndEvents: AnyRef*): PipelineRun = {
      val run = new PipelineRun(stage, context)
      cmdsAndEvents foreach {
        case x: Do => x.run()
        case x: Command => run.pipelines.commandPipeline(x)
        case x: Event   => run.pipelines.eventPipeline(x)
      }
      run
    }
  }

  class PipelineRun(stage: PipelineStage, context: PipelineContext) {
    private val _commands = ListBuffer.empty[Command]
    private val _events = ListBuffer.empty[Event]
    def commands: Seq[Command] = _commands
    def events: Seq[Event] = _events
    def commandsAndEvents: (Seq[Command], Seq[Event]) = (commands, events)
    val pipelines = stage.buildPipelines(context, x => _commands += x, x => _events += x)
  }

  class Do(body: => Unit) extends Command {
    def run() { body }
  }

  def Received(rawMessage: String) = IoWorker.Received(dummyHandle, string2ByteBuffer(rawMessage))
  def Send(rawMessage: String) = IoPeer.Send(string2ByteBuffer(rawMessage))
  def SendString(rawMessage: String) = SendStringCommand(rawMessage)
  def Do(body: => Unit) = new Do(body)
  def Sleep(duration: String) = Do(Thread.sleep(Duration(duration).toMillis))

  case class SendStringCommand(string: String) extends Command

  implicit def pimpCommandSeq[T](commands: Seq[Command]) = new PimpedCommandSeq[T](commands)
  class PimpedCommandSeq[T](underlying: Seq[Command]) {
    def fixSends = underlying.map {
      case IoPeer.Send(bufs, _) => SendStringCommand {
        val sb = new java.lang.StringBuilder
        for (b <- bufs) while (b.remaining > 0) sb.append(b.get.toChar)
        sb.toString.fastSplit('\n').map {
          case s if s.startsWith("Date:") => "Date: XXXX\r"
          case s => s
        }.mkString("\n")
      }
      case x => x
    }
    def fixTells = underlying.map {
      case x: IoPeer.Tell => x.copy(sender = null)
      case x => x
    }
  }

  protected def string2ByteBuffer(s: String) = ByteBuffer.wrap(s.getBytes("US-ASCII"))

  def cleanup() {
    system.shutdown()
  }
}
