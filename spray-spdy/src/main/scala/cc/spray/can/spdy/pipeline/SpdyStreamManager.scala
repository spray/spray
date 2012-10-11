package cc.spray.can.spdy
package pipeline

import cc.spray.io.pipelining._
import cc.spray.io.{IOServer, Event, Command}
import java.nio.ByteBuffer
import cc.spray.util.Reply
import pipeline.SpdyParsing.SpdyFrameReceived
import pipeline.SpdyRendering.SendSpdyFrame
import akka.event.LoggingAdapter

object SpdyStreamManager {
  def apply(messageHandler: MessageHandler, eventExtractor: Event => Any, log: LoggingAdapter)(innerPipeline: PipelineStage): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      val handler = messageHandler(context)()

      val incomingStreams = collection.mutable.Map.empty[Int, SpdyContext]

      def eventPipeline: EPL = {
        case SpdyFrameReceived(frame) =>
          frame match {
            case SynStream(streamId, associatedTo, priority, fin, uni, headers) =>
              val ctx = createStreamContext(streamId)
              ctx.pipelines.eventPipeline(StreamOpened(headers, fin))

            case RstStream(streamId, statusCode) =>
              val ctx = contextFor(streamId)
              ctx.close()
              ctx.pipelines.eventPipeline(StreamAborted(statusCode))

            case x: Settings =>
              println("Ignoring settings for now "+x)

            case Ping(id, data) =>
              commandPL(IOServer.Send(ByteBuffer.wrap(data)))

            case DataFrame(streamId, fin, data) =>
              val ctx = contextFor(streamId)
              ctx.pipelines.eventPipeline(StreamDataReceived(data, fin))
          }
        case x => eventPL(x)
      }

      def commandPipeline: CPL = commandPL

      def createStreamContext(streamId: Int): SpdyContext = {
        if (incomingStreams.contains(streamId))
          throw new IllegalStateException("Tried to create a stream twice "+streamId)

        val res = streamContextFor(streamId)
        incomingStreams(streamId) = res
        res
      }
      def contextFor(streamId: Int): SpdyContext = {
        if (incomingStreams.contains(streamId))
          incomingStreams(streamId)
        else
          throw new IllegalStateException("Tried to access invalid stream")
      }

      def streamContextFor(_streamId: Int): SpdyContext =
        new SpdyContext { spdyCtx =>
          var streamClosed = false
          var lastSender = handler
          def streamId: Int = _streamId
          val pipelines: Pipelines = innerPipeline.buildPipelines(context, baseStreamCommandPipeline, baseStreamEventPipeline)

          def baseStreamEventPipeline: EPL = {
            case event =>
              commandPL(IOServer.Tell(lastSender, eventExtractor(event), Reply.withContext(spdyCtx)(context.connectionActorContext.self)))
          }
          def baseStreamCommandPipeline: CPL = {
            case StreamReply(headers, fin) =>
              send(SynReply(streamId, fin, headers), fin)

            case StreamSendData(data, fin) =>
              send(DataFrame(streamId, fin, data), fin)

            case StreamAbort(cause) =>
              send(RstStream(streamId, cause), true)
          }

          def close() {
            close(true)
          }
          def close(shouldDo: Boolean) {
            if (streamClosed) {
              throw new IllegalArgumentException("Can't operate on closed connection")
            } else if (shouldDo) {
              streamClosed = true
              incomingStreams.remove(streamId)
            }
          }
          def send(frame: Frame, shouldClose: Boolean) {
            if (!streamClosed) {
              lastSender = context.connectionActorContext.sender

              sendFrame(frame)
              close(shouldClose)
            }
            else
              log.warning("Tried to send to closed stream: "+frame)
          }
        }
      def sendFrame(frame: Frame) {
        commandPL(SendSpdyFrame(frame))
      }
    }
  }
  trait SpdyContext {
    def streamId: Int
    def pipelines: Pipelines

    def close()
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
