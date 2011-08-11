package cc.spray
package connectors

import akka.util.AkkaLoader
import akka.actor.{Actor, BootableActorLoaderService}
import org.apache.deft.io.IOLoop
import org.apache.deft.io.buffer.DynamicByteBuffer
import org.apache.deft.web.handler.RequestHandler
import org.apache.deft.web.http.{HttpResponse, HttpRequest}
import org.apache.deft.web.HttpServer
import org.apache.deft.annotation.Asynchronous
import org.apache.deft.configuration.{Configuration => DeftConfig}
import org.apache.deft.web.http.protocol.HttpStatus
import http.HttpCharsets
import utils.ActorHelpers._
import java.io.{OutputStream, ByteArrayInputStream}
import utils.{NotImplementedException, Logging}
import java.util.{HashMap => JHashMap}

trait DeftRunner extends Logging {
  val loader = new AkkaLoader
  var server: HttpServer = _

  def boot() {
    loader.boot(true, new BootableActorLoaderService {})
    configureDeftServer()
    Actor.actorOf(new Actor() {
      self.id = SpraySettings.DeftShutdownActorId
      protected def receive = { case 'shutdown => IOLoop.INSTANCE.stop() }
    }).start()
  }

  def configureDeftServer() {
    log.debug("Starting up Deft server on port " + SpraySettings.DeftPort)
    server = new DeftHttpServer(make(new DeftConfig){ _.setHandlerPackage("none") })
    server.listen(SpraySettings.DeftPort)
  }

  def run() {
    IOLoop.INSTANCE.start() // blocks until IOLoop.INSTANCE.stop() is called
  }

  def shutdown() {
    server.stop()
    loader.shutdown()
  }
}

class DeftHttpServer(config: DeftConfig) extends HttpServer(config) {
  override def createApplication(packageName: String) = new DeftApplication
}

class DeftApplication extends org.apache.deft.web.Application(new JHashMap()) {
  val handler = new DeftRequestHandler
  override def getHandler(request: HttpRequest) = handler
}

class DeftRequestHandler extends RequestHandler with Logging {
  val rootService = actor(SpraySettings.RootActorId)

  @Asynchronous override def get   (req: HttpRequest, res: HttpResponse) { rootService ! context("GET"   , req, res) }
  @Asynchronous override def post  (req: HttpRequest, res: HttpResponse) { rootService ! context("POST"  , req, res) }
  @Asynchronous override def put   (req: HttpRequest, res: HttpResponse) { rootService ! context("PUT"   , req, res) }
  @Asynchronous override def delete(req: HttpRequest, res: HttpResponse) { rootService ! context("DELETE", req, res) }
  @Asynchronous override def head  (req: HttpRequest, res: HttpResponse) { rootService ! context("HEAD"  , req, res) }

  def context(method: String, request: HttpRequest, response: HttpResponse) = {
    RawRequestContext(rawRequest(method, request), completer(response))
  }

  def rawRequest(methodName: String, req: HttpRequest) = new RawRequest {
    def method = methodName
    lazy val uri = req.getRequestLine.fastSplit(' ').apply(1)
    lazy val headers = collection.JavaConversions.mapAsScalaMap(req.getHeaders)
    lazy val inputStream = new ByteArrayInputStream(req.getBody.getBytes(HttpCharsets.`ISO-8859-1`.nioCharset))
    def remoteIP = req.getRemoteHost.toString
    def protocol = req.getVersion
  }

  def rawResponse(response: HttpResponse) = new RawResponse {
    lazy val responseData = {
      // TODO: replace with direct access once HttpResponse.responseData is not private anymore
      val field = response.getClass.getDeclaredField("responseData")
      field.setAccessible(true)
      field.get(response).asInstanceOf[DynamicByteBuffer]
    }
    def setStatus(code: Int) { response.setStatus(HttpStatus.values.find(_.code == code).get) }
    def addHeader(name: String, value: String) { response.setHeader(name, value) }
    lazy val outputStream = new OutputStream {
      def write(b: Int) { throw new NotImplementedException }
      override def write(buf: Array[Byte], offset: Int, len: Int) {
        if (offset == 0 && len == buf.length) {
          responseData.put(buf)
        }
        else {
          // TODO: push Deft devs to include proper responseData.put(buf, offset, len) overload
          val array = new Array[Byte](len)
          System.arraycopy(buf, offset, array, 0, len)
          responseData.put(array)
        }
      }
    }
  }

  def completer(response: HttpResponse): (RawResponse => Unit) => Unit = { fillResponse =>
    try {
      fillResponse(rawResponse(response))
      response.finish()
    } catch {
      case e: Exception => log.error(e, "Could not complete request")
    }
  }
}