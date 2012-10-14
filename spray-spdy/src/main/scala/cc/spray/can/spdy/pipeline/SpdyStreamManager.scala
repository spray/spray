package cc.spray.can.spdy
package pipeline

import java.nio.ByteBuffer

import akka.event.LoggingAdapter

import cc.spray.util.Reply
import cc.spray.io._

import pipeline.SpdyParsing.SpdyFrameReceived
import pipeline.SpdyRendering.SendSpdyFrame


object SpdyStreamManager {
  def apply(eventExtractor: Event => Any, log: LoggingAdapter, client: Boolean = false)(innerPipeline: PipelineStage): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      val incomingStreams = collection.mutable.Map.empty[Int, SpdyContext]

      def eventPipeline: EPL = {
        case SpdyFrameReceived(frame) =>
          frame match {
            case SynStream(streamId, associatedTo, priority, fin, uni, headers) =>
              val ctx = createStreamContext(streamId, Frontend.MessageHandler)
              ctx.pipelines.eventPipeline(StreamOpened(headers, fin))

            case SynReply(streamId, fin, headers) =>
              val ctx = contextFor(streamId)
              ctx.pipelines.eventPipeline(StreamReplied(headers, fin))

            case RstStream(streamId, statusCode) =>
              val ctx = contextFor(streamId)
              ctx.close()
              ctx.pipelines.eventPipeline(StreamAborted(statusCode))

            case x: Settings =>
              println("Ignoring settings for now "+x)

            case Ping(id, data) =>
              commandPL(IOServer.Send(ByteBuffer.wrap(data)))

            case d@DataFrame(streamId, fin, data) =>
              //println("Got DataFrame "+d)
              val ctx = contextFor(streamId)
              ctx.pipelines.eventPipeline(StreamDataReceived(data, fin))
          }
        case x => eventPL(x)
      }

      var upcomingStreamId = if (client) 1 else 2
      def nextStreamId(): Int = {
        val res = upcomingStreamId
        upcomingStreamId += 2
        res
      }

      def commandPipeline: CPL = {
        case StreamOpen(headers, finished) =>
          val id = nextStreamId()
          createStreamContext(id, Frontend.Other(context.sender))
          sendFrame(SynStream(id, 0, 0, finished, false, headers))
        case c => commandPL(c)
      }

      def createStreamContext(streamId: Int, endpoint: Frontend.Receiver, associatedTo: Option[Int] = None): SpdyContext = {
        if (incomingStreams.contains(streamId))
          throw new IllegalStateException("Tried to create a stream twice "+streamId)

        val res = streamContextFor(streamId, endpoint, associatedTo)
        incomingStreams(streamId) = res
        res
      }
      def contextFor(streamId: Int): SpdyContext = {
        if (incomingStreams.contains(streamId))
          incomingStreams(streamId)
        else
          throw new IllegalStateException("Tried to access invalid stream")
      }

      def streamContextFor(_streamId: Int, endpoint: Frontend.Receiver, associatedTo: Option[Int]): SpdyContext =
        new SpdyContext { spdyCtx =>
          var streamClosed = false
          var lastSender: Frontend.Receiver = endpoint
          def streamId: Int = _streamId
          val pipelines: Pipelines = innerPipeline.buildPipelines(context, baseStreamCommandPipeline, baseStreamEventPipeline)

          def baseStreamEventPipeline: EPL = {
            case event =>
              commandPL(Frontend.Tell(lastSender, eventExtractor(event), Reply.withContext(spdyCtx)(context.connectionActorContext.self)))
          }
          def baseStreamCommandPipeline: CPL = {
            case StreamReply(headers, fin) =>
              if (associatedTo.isEmpty)
                send(SynReply(streamId, fin, headers), fin)
              else {
                println("Got associated stream")
                // HACK: removing the url here is http specific
                val hs = headers.filterKeys(_ != "url")
                //println("Headers "+hs)
                send(Headers(streamId, hs), false)
                //if (fin)
                //  baseStreamCommandPipeline(StreamSendData(Array.empty, true))
                //send(SynStream(streamId, associatedTo.get, 0, fin, true, headers), fin)
              }

            case StreamSendData(data, fin) =>
              send(DataFrame(streamId, fin, data), fin)

            case StreamAbort(cause) =>
              send(RstStream(streamId, cause), true)

            case StreamOpenAssociated(headers, withCtx) =>
              val assocId = nextStreamId()
              send(SynStream(assocId, streamId, 0, false, false, headers), false)
              withCtx(createStreamContext(assocId, lastSender, Some(streamId)))
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
              lastSender = Frontend.Other(context.connectionActorContext.sender)

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
  case class StreamReplied(header: Map[String, String], finished: Boolean) extends Event
  case class StreamDataReceived(data: Array[Byte], finished: Boolean) extends Event
  case class StreamAborted(cause: Int) extends Event

  // COMMANDS
  case class StreamOpen(headers: Map[String, String], finished: Boolean) extends Command
  case class StreamOpenAssociated(headers: Map[String, String], withCtx: SpdyContext => Unit) extends Command
  case class StreamReply(headers: Map[String, String], finished: Boolean) extends Command
  case class StreamSendData(data: Array[Byte], finished: Boolean) extends Command
  case class StreamAbort(cause: Int) extends Command
}
