package cc.spray.can.spdy
package pipeline

import java.nio.ByteBuffer

import cc.spray.io.pipelining._
import cc.spray.io.{IOServer, Command, Event}
import cc.spray.util.Reply

import cc.spray.http._
import cc.spray.http.HttpHeaders.RawHeader
import cc.spray.http.HttpResponse
import pipeline.SpdyParsing.SpdyFrameReceived
import pipeline.SpdyRendering.SendSpdyFrame
import cc.spray.can.{HttpEvent, HttpCommand}

object HttpOnSpdy {
  def apply(messageHandler: MessageHandler)(innerPipeline: PipelineStage): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {
      val handler = messageHandler(context)()

      def eventPipeline: (Event) => Unit = {
        case SpdyFrameReceived(frame) =>
          frame match {
            case x: SynStream =>
              println("Got syn stream "+x)
              if (x.fin) {
                val req = requestFromKV(x.keyValues)
                val ctx = createStreamContext(x.streamId)
                ctx.pipelines.eventPipeline(HttpEvent(req))
              } else
                throw new UnsupportedOperationException("Can't handle requests with contents, right now")

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

      def createStreamContext(_streamId: Int): SpdyContext = new SpdyContext { ctx =>
        def streamId: Int = _streamId
        val pipelines: Pipelines = innerPipeline.buildPipelines(context, createStreamCommandPipeline, createStreamEventPipeline)

        def createStreamEventPipeline: EPL = unpackHttpEvent {
          case req: HttpRequest =>

            commandPL(IOServer.Tell(handler, req, Reply.withContext(ctx)(context.connectionActorContext.self)))
        }
        def createStreamCommandPipeline: CPL = unpackHttpCommand {
          case response: HttpResponse =>
            println("Got reply for "+streamId)

            val fin = response.entity.buffer.isEmpty
            send(SynReply(streamId, fin, responseToKV(response)))

            if (!fin)
              send(DataFrame(streamId, true, response.entity.buffer))

          case ChunkedResponseStart(response) =>
            val fin = response.entity.buffer.isEmpty
            send(SynReply(streamId, fin, responseToKV(response)))

            if (!fin)
              send(DataFrame(streamId, false, response.entity.buffer))

          case MessageChunk(body, exts) =>

            send(DataFrame(streamId, false, body))

          case response: ChunkedMessageEnd =>
            send(DataFrame(streamId, true, Array.empty))
        }
      }

      def unpackHttpEvent(inner: HttpRequestPart => Unit): EPL = {
        case HttpEvent(e: HttpRequestPart) => inner(e)
        case e => eventPL(e)
      }
      def unpackHttpCommand(inner: HttpResponsePart => Unit): CPL = {
        case HttpCommand(c: HttpResponsePart) => inner(c)
        case c => commandPL(c)
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

  trait SpdyContext {
    def streamId: Int
    def pipelines: Pipelines
  }
  case class CommandWithSpdyCtx(ctx: SpdyContext, msg: Any) extends Command
}
