package cc.spray
package client

import http._
import HttpHeaders._
import com.ning.http.client.{HttpContent => _, _}
import akka.dispatch._
import java.util.concurrent.{ExecutionException, Executor}
import java.io.ByteArrayOutputStream
import collection.JavaConversions._
import org.parboiled.common.FileUtils
import akka.event.EventHandler
import akka.actor.Actor

/**
 * The central class encapsulating the Ning [[https://github.com/sonatype/async-http-client AsyncHttpClient]] as a scala wrapper.
 * Optionally takes a {{responseMock}} function, which allows you do selectively mock [[cc.spray.HttpResponse]]s for certain
 *  [[cc.spray.HttpRequest]]s. Very useful for testing.
 * For all documentation regarding configuration parameters please see the
 *  [[http://sonatype.github.com/async-http-client/apidocs/index.html AHC Javadocs]].
 */
class HttpClient(val ahc: AsyncHttpClient, responseMock: HttpRequest => Option[HttpResponse] = { _ => None }) {

  def this(config: ClientConfig) = this(new AsyncHttpClient(config.toAhcConfig))
  def this(provider: AsyncHttpProvider) = this(new AsyncHttpClient(provider))
  def this(provider: AsyncHttpProvider, config: ClientConfig) = this(new AsyncHttpClient(provider, config.toAhcConfig))
  def this(providerClassName: String, config: ClientConfig) = this(new AsyncHttpClient(providerClassName, config.toAhcConfig))

  def withResponseMock(mock: HttpRequest => Option[HttpResponse]) = new HttpClient(ahc, mock)
  
  lazy val defaultHandler = new AsyncCompletionHandler[HttpResponse] {
    def onCompleted(response: Response) = toSprayResponse(response)
  }

  /**
   * Dispatches the given HttpRequest, optionally using the given requestConfig and/or
   *  [[http://sonatype.github.com/async-http-client/apidocs/com/ning/http/client/AsyncHandler.html AsyncHandler]].
   */
  def dispatch(request: HttpRequest, requestConfig: RequestConfig = null,
               handler: AsyncHandler[HttpResponse] = defaultHandler): Future[HttpResponse] = {
    responseMock(request) match {
      case Some(response) => new AlreadyCompletedFuture(Right(response))
      case None => performDispatch(request, requestConfig, handler)
    }
  }
  
  protected def performDispatch(request: HttpRequest, requestConfig: RequestConfig,
                                handler: AsyncHandler[HttpResponse]): Future[HttpResponse] = {
    EventHandler.debug(this, "Dispatching HTTP request:\n" + request)    
    val akkaFuture = new DefaultCompletableFuture[HttpResponse](Long.MaxValue)
    val ahcRequest = fromSprayRequest(request, requestConfig)
    val ahcFuture = ahc.executeRequest(ahcRequest, handler)    
    ahcFuture.addListener(new Runnable {
      def run() {
        try {
          akkaFuture.completeWithResult(ahcFuture.get)
        } catch {
          case e: ExecutionException => {
            val cause = e.getCause
            EventHandler.error(cause, this, "Could not get HTTP response from remote server")
            akkaFuture.completeWithException(cause)
          }
          case e => {
            EventHandler.error(e, this, "Could not get HTTP response from remote server")
            akkaFuture.completeWithException(e)
          }
        }
      }
    }, new Executor {
      def execute(command: Runnable) {
        Actor.spawn { command.run() }
      }
    })
    akkaFuture
  }
  
  protected def fromSprayRequest(req: HttpRequest, requestConfig: RequestConfig) = {
    val headers = new FluentCaseInsensitiveStringsMap
    for (header <- req.headers) headers.put(header.name, List(header.value))
    
    val builder = new RequestBuilder()
            .setMethod(req.method.value)
            .setUrl(req.uri)
            .setHeaders(headers)
    if (requestConfig != null) requestConfig.configure(builder)
    if (req.content.isDefined) builder.setBody(req.content.get.buffer)
    builder.build()
  }
  
  protected def toSprayResponse(resp: Response) = {
    val allHeaders = resp.getHeaders.entrySet().toList.map { entry =>
      HttpHeader(entry.getKey, entry.getValue.get(0))        
    }
    allHeaders.partition(_.isInstanceOf[`Content-Type`]) match {
      case (`Content-Type`(contentType, _) :: _, headers) => {
        val buf = new ByteArrayOutputStream
        FileUtils.copyAll(resp.getResponseBodyAsStream, buf)
        HttpResponse(resp.getStatusCode, headers, HttpContent(contentType, buf.toByteArray))
      }
      case _ => HttpResponse(resp.getStatusCode, allHeaders) 
    } 
  }

  /**
   * Closes all underlying connections.
   */
  def close() {
    ahc.close()
  }
  
}