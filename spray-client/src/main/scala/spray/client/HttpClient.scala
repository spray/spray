/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.client

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal
import akka.actor.{Status, Props, ActorRef, Actor}
import spray.can.client.{HttpClientConnection, HttpClientConnectionSettings}
import spray.util._
import spray.http._


/**
 * The top-level HTTP transport.
 * Manages a number of HttpHostConnectors (one per host/port combination) and routes
 * incoming HttpRequests to the right one according to their Host header.
 */
class HttpClient(val httpClientSettings: HttpClientSettings = HttpClientSettings(),
                 val hostConnectorSettings: HttpHostConnectorSettings = HttpHostConnectorSettings(),
                 val clientConnectionSettings: HttpClientConnectionSettings = HttpClientConnectionSettings())
  extends Actor with SprayActorLogging {

  import HttpClient._
  import httpClientSettings._

  private[this] val connectors = mutable.HashMap.empty[(String, Int), Connector]

  if (httpClientSettings.PruningCycle > 0) {
    val cycle = Duration(PruningCycle, MILLISECONDS)
    context.system.scheduler.schedule(cycle, cycle, self, Prune(PruningShare))
  }

  def receive: Receive = {
    case request: HttpRequest =>
      request.hostHeader match {
        case Some(HttpHeaders.Host(name, port)) => dispatch(request, name, port)
        case None =>
          try dispatchFromUri(request) catch {
            case NonFatal(e) => sender ! Status.Failure(e)
          }
      }

    case Prune(share) =>
      val random = new java.util.Random
      val selected = connectors.toList.filter(_ => random.nextDouble <= share)
      selected.foreach {
        case (hostPort, Connector(ref, _, PruningSelectionLimit)) =>
          connectors.remove(hostPort)
          context.stop(ref)
        case (hostPort, Connector(ref, encrypted, count)) =>
          ref ! HttpHostConnector.RequestIdleStatus
          connectors(hostPort) = Connector(ref, encrypted, count + 1)
      }

    case HttpHostConnector.IdleStatus(true, host, port) =>
      val hostPort = host.toLowerCase -> port
      connectors.get(hostPort).foreach {
        case Connector(ref, _, 0) => // connector received a request in the mean time, so don't prune
        case Connector(ref, _, _) =>
          log.debug("Shutting down idle HttpHostConnector for {}:{}", host, port)
          connectors.remove(hostPort)
          context.stop(ref)
      }
  }

  def dispatch(request: HttpRequest, host: String, port: Option[Int]) {
    dispatch(request, host, port.getOrElse(80), port == Some(443))
  }

  def dispatchFromUri(request: HttpRequest) {
    val parsedRequest = request.parseUri
    import parsedRequest._
    if (scheme.isEmpty || uriHost.isEmpty)
      sys.error("Request %s has relative URI and is missing `Host` header" format request)
    val (port, ssl) = scheme.toLowerCase match {
      case "http"  => uriPort.getOrElse(80) -> false
      case "https" => uriPort.getOrElse(443) -> true
      case x => sys.error("Invalid request scheme: " + x)
    }
    val patchedRequest = request.copy(uri = rawPathQueryFragment, headers = HttpHeaders.Host(uriHost, port) :: headers)
    dispatch(patchedRequest, uriHost, port, ssl)
  }

  def dispatch(request: HttpRequest, host: String, port: Int, ssl: Boolean) {
    val hostPort = host.toLowerCase -> port
    val Connector(ref, enc, count) =
      connectors.getOrElseUpdate(hostPort, Connector(createConnector(host, port, ssl), ssl))
    (ssl, enc) match {
      case (true, false) => sys.error("Cannot enable encryption for target host/port that was previously unencrypted")
      case (false, true) => sys.error("Cannot disable encryption for target host/port that was previously encrypted")
      case _ =>
    }
    if (count > 0) connectors(hostPort) = Connector(ref, ssl) // cancel pruning
    ref.forward(request)
  }

  def createConnector(host: String, port: Int, ssl: Boolean): ActorRef =
    context.actorOf(Props(
      new HttpHostConnector(host, port, hostConnectorSettingsFor(host, port), clientConnectionSettingsFor(host, port)) {
        override def tagForConnection(index: Int): Any = connectionTagFor(host, port, index, ssl)
      }
    ))

  // override for custom settings specification
  def hostConnectorSettingsFor(host: String, port: Int): HttpHostConnectorSettings = hostConnectorSettings

  // override for custom settings specification
  def clientConnectionSettingsFor(host: String, port: Int): HttpClientConnectionSettings = clientConnectionSettings

  // override for custom tag specification
  def connectionTagFor(host: String, port: Int, connectionIndex: Int, ssl: Boolean): Any =
    if (ssl) HttpClientConnection.SslEnabled else ()
}

object HttpClient {
  private case class Connector(ref: ActorRef, encrypted: Boolean, pruningSelectionCount: Int = 0)

  case class Prune(share: Double) {
    require(0.0 <= share && share <= 1.0, "share must be >= 0.0 and <= 1.0")
  }
}