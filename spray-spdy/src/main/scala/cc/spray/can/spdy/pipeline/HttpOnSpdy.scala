package cc.spray.can.spdy
package pipeline

import cc.spray.io._

import cc.spray.http._
import cc.spray.http.HttpHeaders.RawHeader
import cc.spray.http.HttpResponse
import cc.spray.can.{HttpEvent, HttpCommand}
import cc.spray.can.server.HttpServer

import parser.HttpParser
import pipeline.SpdyStreamManager._
import server.SpdyHttpServer.ServerPush

object HttpOnSpdy {
  def apply(client: Boolean = false): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {

      def eventPipeline: EPL = {
        case StreamOpened(headers, finished) =>
          if (finished) {
            eventPL(HttpEvent(requestFromKV(headers)))
          } else
            throw new UnsupportedOperationException("Can't handle requests with contents, right now")
        // TODO: implement request content handling
        // case StreamDataReceived(data, finished) =>

        case StreamReplied(headers, finished) =>
          val response = responseFromKV(headers)
          //println("Got response "+response)
          if (finished)
            eventPL(HttpEvent(response))
          else
            eventPL(HttpEvent(ChunkedResponseStart(response)))

        case StreamDataReceived(data, finished) =>
          if (data.length > 0)
          eventPL(HttpEvent(MessageChunk(data)))

          if (finished) {
            //println("All data received")
            eventPL(HttpEvent(ChunkedMessageEnd()))
          }

        case StreamAborted(cause) =>
          // TODO: properly translate cause
          eventPL(HttpServer.Closed(context.handle, PeerClosed))
        case x => eventPL(x)
      }

      def commandPipeline: CPL = unpackHttpCommand {
        case response: HttpResponse =>
          val fin = response.entity.isEmpty
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
      def unpackHttpCommand(inner: HttpMessagePart => Unit): Command => Unit = {
        case HttpCommand(c: HttpMessagePart) => inner(c)

        case ServerPush(request) =>
          println("Got push")
          require(request.entity.isEmpty)
          commandPL(StreamOpenAssociated(Map("url" -> ("https://localhost:8081"+request.uri)), { ctx =>
            println("Running pushed request")
            ctx.pipelines.eventPipeline(HttpEvent(request))
          }))
        case c => commandPL(c)
      }
    }
  }

  def acceptRequests(): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      def eventPipeline = eventPL

      def commandPipeline = {
        case HttpCommand(request: HttpRequest) =>
          val fin = request.entity.isEmpty
          commandPL(StreamOpen(requestToKV(request), fin))

          if (!fin)
            commandPL(StreamSendData(request.entity.buffer, true))
        case c => commandPL(c)
      }
    }
  }

  // TODO: make parsing more resilient against invalid values
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

  val SpecialResponseKeys = Set("status", "version")
  def responseFromKV(kv: Map[String, String]): HttpResponse = {
    val status = kv("status").split(" ")(0).toInt
    val version = kv("version")
    val contentType = HttpParser.parseContentType(kv("content-type")).right.get

    val headers = kv.filterNot(kv => SpecialResponseKeys(kv._1)).map {
      case (k, v) => RawHeader(k, v)
    }
    val proto = HttpProtocols.getForKey(version).get

    val body = HttpBody(contentType, Array.empty[Byte])
    HttpResponse(status, body, headers.toList, proto)
  }

  def requestToKV(request: HttpRequest): Map[String, String] = {
    Map(
      "version" -> request.protocol.value,
      /*"scheme" -> "http",
      "path" -> request.path,
      "host" -> request.hostAndPort,*/
      "url" -> request.uri,
      "method" -> request.method.value
    ) ++ request.headers.map { header =>
      (header.name.toLowerCase, header.value)
    }
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
