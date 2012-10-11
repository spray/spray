package cc.spray.can.spdy
package pipeline

import cc.spray.io.pipelining._
import cc.spray.io.{IOServer, Event, Command}
import java.nio.ByteBuffer
import cc.spray.util.Reply
import pipeline.SpdyParsing.SpdyFrameReceived
import pipeline.SpdyRendering.SendSpdyFrame

object SpdyStreamManager {
  def apply(messageHandler: MessageHandler, eventExtractor: Event => Any)(innerPipeline: PipelineStage): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {
      val handler = messageHandler(context)()

      //val streamCtx = collection.mutable.Map.empty[Int, SpdyContext]

      def eventPipeline: (Event) => Unit = {
        case SpdyFrameReceived(frame) =>
          frame match {
            case x: SynStream =>
              val ctx = createStreamContext(x.streamId)
              ctx.pipelines.eventPipeline(StreamOpened(x.keyValues, x.fin))

            case x: RstStream =>
              println("Stream got cancelled "+x)

            case x: Settings =>
              println("Ignoring settings for now "+x)

            case Ping(id, data) =>
              println("Got ping "+id)

              commandPL(IOServer.Send(ByteBuffer.wrap(data)))

            case d: DataFrame =>
              throw new UnsupportedOperationException("Receiving data not supported currently")
          }
        case x => eventPL(x)
      }

      def commandPipeline: (Command) => Unit = {
        case x => commandPL(x)
      }

      def createStreamContext(_streamId: Int): SpdyContext = new SpdyContext { spdyCtx =>
        def streamId: Int = _streamId
        val pipelines: Pipelines = innerPipeline.buildPipelines(context, baseStreamCommandPipeline, baseStreamEventPipeline)

        def baseStreamEventPipeline: EPL = {
          case event =>
            commandPL(IOServer.Tell(handler, eventExtractor(event), Reply.withContext(spdyCtx)(context.connectionActorContext.self)))
        }
        def baseStreamCommandPipeline: CPL = {
          case StreamReply(headers, fin) =>
            send(SynReply(streamId, fin, headers))
          case StreamSendData(data, fin) =>
            send(DataFrame(streamId, fin, data))
        }
      }

      def send(frame: Frame) {
        commandPL(SendSpdyFrame(frame))
      }
    }
  }
  trait SpdyContext {
    def streamId: Int
    def pipelines: Pipelines
  }

  // EVENTS
  case class StreamOpened(headers: Map[String, String], finished: Boolean) extends Event
  case class StreamDataReceived(data: Array[Byte], finished: Boolean) extends Event
  case class StreamAborted(cause: Int) extends Event

  // COMMANDS
  case class StreamReply(headers: Map[String, String], finished: Boolean) extends Command
  case class StreamSendData(data: Array[Byte], finished: Boolean) extends Command
  case class StreamAbort(cause: Int) extends Command
}
