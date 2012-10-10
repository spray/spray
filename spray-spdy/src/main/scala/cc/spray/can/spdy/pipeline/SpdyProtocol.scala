package cc.spray.can.spdy

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

object SpdyProtocol {
  def apply(messageHandler: MessageHandler): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {
      val inflater = new Inflater()
      def startParser = new parsing.FrameHeaderParser(inflater)

      val renderer = new rendering.SpdyRenderer

      var currentParsingState: ParsingState = startParser
      val handler = messageHandler(context)()

      @tailrec
      def parse(buffer: ByteBuffer) {
        currentParsingState match {
          case x: IntermediateState =>
            if (buffer.remaining > 0) {
              currentParsingState = x.read(buffer)
              parse(buffer)
            } // else wait for more input

          case x: SynStream =>
            println("Got syn stream "+x)
            if (x.fin) {
              val req = requestFromKV(x.keyValues)
              commandPL(IOServer.Tell(handler, req, Reply.withContext(x.streamId)(context.connectionActorContext.self)))
            }

            currentParsingState = startParser
            parse(buffer)

          case x: RstStream =>
            println("Stream got cancelled "+x)
            currentParsingState = startParser
            parse(buffer)

          case x: Settings =>
            println("Ignoring settings for now "+x+" remaining bytes "+buffer.remaining())

            currentParsingState = startParser
            parse(buffer)

          case Ping(id, data) =>
            println("Got ping "+id)

            commandPL(IOServer.Send(ByteBuffer.wrap(data)))

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
        case ReplyToStream(streamId, response, dataComplete) =>
          val fin = response.entity.buffer.isEmpty
          commandPL(IOServer.Send(renderer.renderSynReply(streamId, fin, responseToKV(response))))

          if (!fin)
            commandPL(IOServer.Send(renderer.renderDataFrame(streamId, dataComplete, response.entity.buffer)))

        case SendStreamData(streamId, data) =>
          commandPL(IOServer.Send(renderer.renderDataFrame(streamId, false, data)))

        case CloseStream(streamId) =>
          commandPL(IOServer.Send(renderer.renderDataFrame(streamId, true, Array.empty)))

        case x =>
          println("Got command "+x)
          commandPL(x)
      }
    }
  }

  val SpecialKeys = Set("method", "version", "url")
  def requestFromKV(kv: Map[String, String]): HttpRequest = {

    val method = HttpMethods.getForKey(kv("method")).get
    val uri = kv("url")
    val headers = kv.filterNot(kv => SpecialKeys(kv._1)).map {
      case (k, v) => RawHeader(k, v)
    }.toList
    val protocol = HttpProtocols.getForKey(kv("version")).get

    HttpRequest(method = method, uri = uri, headers = headers, protocol = protocol)
  }
  def responseToKV(response: HttpResponse): Map[String, String] = {
    println("Headers "+response.headers)
    Map(
      "status" -> response.status.value.toString,
      "version" -> response.protocol.value,
      "content-type" -> response.entity.asInstanceOf[HttpBody].contentType.value
    ) ++ response.headers.map { header =>
      (header.name.toLowerCase, header.value)
    }
  }

  case class ReplyToStream(streamId: Int, response: HttpResponse, dataComplete: Boolean) extends Command
  case class SendStreamData(streamId: Int, body: Array[Byte]) extends Command
  case class CloseStream(streamId: Int) extends Command
}

object HttpOnSpdy {
  def apply(): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {
      def eventPipeline: (Event) => Unit = {
        case x => eventPL(x)
      }

      def commandPipeline: (Command) => Unit = {
        case x => commandPL(x)
      }
    }
  }
}