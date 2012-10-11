package cc.spray.can.spdy
package pipeline

import cc.spray.io.pipelining._

import cc.spray.http._
import cc.spray.http.HttpHeaders.RawHeader
import cc.spray.http.HttpResponse
import cc.spray.can.{HttpEvent, HttpCommand}
import cc.spray.can.server.HttpServer
import cc.spray.io.PeerClosed

import pipeline.SpdyStreamManager._

object HttpOnSpdy {
  def apply(): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {

      def eventPipeline: EPL = {
        case StreamOpened(headers, finished) =>
          if (finished) {
            eventPL(HttpEvent(requestFromKV(headers)))
          } else
            throw new UnsupportedOperationException("Can't handle requests with contents, right now")
        // TODO: implement request content handling
        // case StreamDataReceived(data, finished) =>
        case StreamAborted(cause) =>
          // TODO: properly translate cause
          eventPL(HttpServer.Closed(context.handle, PeerClosed))
        case x => eventPL(x)
      }

      def commandPipeline: CPL = unpackHttpCommand {
        case response: HttpResponse =>
          val fin = response.entity.buffer.isEmpty
          commandPL(StreamReply(responseToKV(response), fin))

          if (!fin)
            commandPL(StreamSendData(response.entity.buffer, true))

        case ChunkedResponseStart(response) =>
          val fin = response.entity.buffer.isEmpty
          commandPL(StreamReply(responseToKV(response), fin))

          if (!fin)
            commandPL(StreamSendData(response.entity.buffer, false))

        case MessageChunk(body, exts) =>
          commandPL(StreamSendData(body, false))

        case response: ChunkedMessageEnd =>
          // TODO: maybe use a dedicated Command for (half-)closing a connection
          commandPL(StreamSendData(Array.empty, true))
      }

      def unpackHttpCommand(inner: HttpResponsePart => Unit): CPL = {
        case HttpCommand(c: HttpResponsePart) => inner(c)
        case c => commandPL(c)
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
    Map(
      "status" -> response.status.value.toString,
      "version" -> response.protocol.value,
      "content-type" -> response.entity.asInstanceOf[HttpBody].contentType.value
    ) ++ response.headers.map { header =>
      (header.name.toLowerCase, header.value)
    }
  }
}
