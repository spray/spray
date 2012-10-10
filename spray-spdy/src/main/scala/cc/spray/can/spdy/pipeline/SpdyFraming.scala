package cc.spray.can.spdy
package pipeline

import cc.spray.io.pipelining.{MessageHandler, Pipelines, PipelineContext, DoublePipelineStage}
import cc.spray.io.{ProtocolError, IOServer, Event, Command}
import java.nio.ByteBuffer
import cc.spray.can.parsing.{IntermediateState, ParsingState}
import annotation.tailrec
import java.util.zip.Inflater
import cc.spray.can.server.RequestParsing.HttpMessageStartEvent
import cc.spray.http._
import cc.spray.http.HttpHeaders.RawHeader
import cc.spray.util.Reply
import cc.spray.http.HttpHeaders.RawHeader
import cc.spray.io.ProtocolError
import akka.actor.ActorRef

object SpdyFraming {
  def apply(): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {
      val inflater = new Inflater()
      def startParser = new parsing.FrameHeaderParser(inflater)

      val renderer = new rendering.SpdyRenderer

      var currentParsingState: ParsingState = startParser

      @tailrec
      def parse(buffer: ByteBuffer) {
        currentParsingState match {
          case x: IntermediateState =>
            if (buffer.remaining > 0) {
              currentParsingState = x.read(buffer)
              parse(buffer)
            } // else wait for more input

          case f: Frame =>
            eventPL(SpdyFrameReceived(f))

            currentParsingState = startParser
            parse(buffer)

          case x: FrameParsingError =>
            println("Got error "+x)
            commandPL(IOServer.Close(ProtocolError("Got error "+x)))
            currentParsingState = startParser
        }
      }

      val eventPipeline: EPL = {
        case x: IOServer.Received =>
          //println("Got "+x.buffer.limit()+" bytes "+(x.buffer.get(0) & 0xff).toHexString+" in state "+currentParsingState)
          parse(x.buffer)
          //println("Afterwards in state "+currentParsingState)

        case x =>
          //println("Got "+x)
          eventPL(x)
      }

      def commandPipeline: CPL = {
        case SendSpdyFrame(frame) =>
          commandPL(IOServer.Send(renderer.renderFrame(frame)))

        case x =>
          println("Got command "+x)
          commandPL(x)
      }
    }
  }


  // EVENTS
  case class SpdyFrameReceived(frame: Frame) extends Event

  // COMMANDS
  case class SendSpdyFrame(frame: Frame) extends Command
}

