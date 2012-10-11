package cc.spray.can.spdy
package pipeline

import java.nio.ByteBuffer

import cc.spray.io.pipelining.{MessageHandler, Pipelines, PipelineContext, DoublePipelineStage}
import cc.spray.io.{IOServer, Command, Event}
import cc.spray.util.Reply

import cc.spray.http._
import cc.spray.http.HttpHeaders.RawHeader

import SpdyParsing.SpdyFrameReceived
import SpdyRendering.SendSpdyFrame

object HttpOnSpdy {
  def apply(messageHandler: MessageHandler): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {
      val handler = messageHandler(context)()

      def eventPipeline: (Event) => Unit = {
        case SpdyFrameReceived(frame) =>
          frame match {
            case x: SynStream =>
              println("Got syn stream "+x)
              if (x.fin) {
                val req = requestFromKV(x.keyValues)
                commandPL(IOServer.Tell(handler, req, Reply.withContext(x.streamId)(context.connectionActorContext.self)))
              }

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
        case ReplyToStream(streamId, response, dataComplete) =>
          val fin = response.entity.buffer.isEmpty
          send(SynReply(streamId, fin, responseToKV(response)))

          if (!fin)
            send(DataFrame(streamId, dataComplete, response.entity.buffer))

        case SendStreamData(streamId, data) =>
          send(DataFrame(streamId, false, data))

        case CloseStream(streamId) =>
          send(DataFrame(streamId, true, Array.empty))

        case x => commandPL(x)
      }

      def send(frame: Frame) {
        commandPL(SendSpdyFrame(frame))
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

  // COMMANDS
  case class ReplyToStream(streamId: Int, response: HttpResponse, dataComplete: Boolean) extends Command
  case class SendStreamData(streamId: Int, body: Array[Byte]) extends Command
  case class CloseStream(streamId: Int) extends Command
}
