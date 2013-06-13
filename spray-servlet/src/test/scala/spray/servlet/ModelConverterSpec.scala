/*
 * Copyright (C) 2011-2013 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.servlet

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse, Part }
import javax.servlet._
import java.util
import java.util.Locale
import java.io.{ ByteArrayInputStream, BufferedReader }
import java.security.Principal
import scala.concurrent.duration.Duration
import akka.event.NoLogging
import spray.http._
import HttpHeaders._

class ModelConverterSpec extends Specification with NoTimeConversions {
  implicit def noLogging = NoLogging
  val settings = ConnectorSettings(
    bootClass = "xxx",
    requestTimeout = Duration.Undefined,
    timeoutTimeout = Duration.Undefined,
    timeoutHandler = "",
    rootPath = Uri.Path.Empty,
    remoteAddressHeader = false,
    verboseErrorMessages = true,
    maxContentLength = 16)

  val remoteAddress = `Remote-Address`("127.0.0.7")
  val textPlain = `Content-Type`(ContentTypes.`text/plain`)

  "The ModelConverter" should {
    "correctly extract spray.http.HttpRequest instances from HttpServletRequests" in {
      "example 1" in {
        implicit def s = settings
        ModelConverter.toHttpRequest(RequestMock()) === HttpRequest()
      }
      "example 2" in {
        implicit def s = settings.copy(remoteAddressHeader = true)
        ModelConverter.toHttpRequest(RequestMock()) === HttpRequest(headers = remoteAddress :: Nil)
      }
      "example 3" in {
        implicit def s = settings
        val u = "https://foo.bar/abc/def?a=1&b=2&b=3&c"
        ModelConverter.toHttpRequest(RequestMock(u)) === HttpRequest(uri = u)
      }
      "example 4" in {
        implicit def s = settings.copy(rootPath = Uri.Path("/abc"))
        ModelConverter.toHttpRequest(RequestMock("https://foo.bar/abc/def")) === HttpRequest(uri = "https://foo.bar/def")
      }
      "example 5" in {
        implicit def s = settings
        val c = "foobarbaz"
        ModelConverter.toHttpRequest {
          RequestMock(
            content = c,
            headers = "Content-Type" -> "text/plain" :: Nil)
        } === HttpRequest(entity = c, headers = textPlain :: Nil)
      }
      "example 6" in {
        implicit def s = settings
        ModelConverter.toHttpRequest {
          RequestMock(content = "12345678901234567")
        } must throwAn[IllegalRequestException]
      }
      "example 7" in {
        implicit def s = settings
        ModelConverter.toHttpRequest(RequestMock(headers = "Cookie" -> "foo=bar; bar=baz" :: Nil)) ===
          HttpRequest(headers = Cookie(HttpCookie("foo", "bar"), HttpCookie("bar", "baz")) :: Nil)
      }
    }
  }

  case class RequestMock(uri: Uri = Uri./,
                         method: String = "GET",
                         remoteAddr: String = remoteAddress.ip.value,
                         content: String = "",
                         headers: Seq[(String, String)] = Nil) extends HttpServletRequest {
    import scala.collection.JavaConverters._

    def getAuthType: String = ???
    def getCookies = ???
    def getDateHeader(name: String): Long = ???
    def getHeader(name: String): String = headers collectFirst { case (n, v) if name equalsIgnoreCase n ⇒ v } getOrElse null
    def getHeaders(name: String): util.Enumeration[String] =
      headers.collect { case (n, v) if name equalsIgnoreCase n ⇒ v }.iterator.asJavaEnumeration
    def getHeaderNames: util.Enumeration[String] = headers.map(_._1).iterator.asJavaEnumeration
    def getIntHeader(name: String): Int = ???
    def getMethod: String = method
    def getPathInfo: String = ???
    def getPathTranslated: String = ???
    def getContextPath: String = ???
    def getQueryString: String = if (uri.query.isEmpty) null else uri.query.toString()
    def getRemoteUser: String = ???
    def isUserInRole(role: String): Boolean = ???
    def getUserPrincipal: Principal = ???
    def getRequestedSessionId: String = ???
    def getRequestURI: String = ???
    def getRequestURL: StringBuffer = new StringBuffer(uri.copy(query = Uri.Query.Empty).toString)
    def getServletPath: String = ???
    def getSession(create: Boolean) = ???
    def getSession = ???
    def isRequestedSessionIdValid: Boolean = ???
    def isRequestedSessionIdFromCookie: Boolean = ???
    def isRequestedSessionIdFromURL: Boolean = ???
    def isRequestedSessionIdFromUrl: Boolean = ???
    def authenticate(response: HttpServletResponse): Boolean = ???
    def login(username: String, password: String) {}
    def logout() {}
    def getParts: util.Collection[Part] = ???
    def getPart(name: String): Part = ???
    def getAttribute(name: String): AnyRef = ???
    def getAttributeNames: util.Enumeration[String] = ???
    def getCharacterEncoding: String = ???
    def setCharacterEncoding(env: String) {}
    def getContentLength: Int = content.length
    def getContentType: String = ???
    def getInputStream: ServletInputStream = new ServletInputStream {
      val input = new ByteArrayInputStream(content.getBytes)
      def read(): Int = input.read()
    }
    def getParameter(name: String): String = ???
    def getParameterNames: util.Enumeration[String] = ???
    def getParameterValues(name: String): Array[String] = ???
    def getParameterMap: util.Map[String, Array[String]] = ???
    def getProtocol: String = "HTTP/1.1"
    def getScheme: String = uri.scheme
    def getServerName: String = ???
    def getServerPort: Int = ???
    def getReader: BufferedReader = ???
    def getRemoteAddr: String = remoteAddr
    def getRemoteHost: String = ???
    def setAttribute(name: String, o: Any) {}
    def removeAttribute(name: String) {}
    def getLocale: Locale = ???
    def getLocales: util.Enumeration[Locale] = ???
    def isSecure: Boolean = ???
    def getRequestDispatcher(path: String): RequestDispatcher = ???
    def getRealPath(path: String): String = ???
    def getRemotePort: Int = ???
    def getLocalName: String = ???
    def getLocalAddr: String = ???
    def getLocalPort: Int = ???
    def getServletContext: ServletContext = ???
    def startAsync(): AsyncContext = ???
    def startAsync(servletRequest: ServletRequest, servletResponse: ServletResponse): AsyncContext = ???
    def isAsyncStarted: Boolean = ???
    def isAsyncSupported: Boolean = ???
    def getAsyncContext: AsyncContext = ???
    def getDispatcherType: DispatcherType = ???
  }
}
