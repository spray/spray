/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package client

import utils._
import http._
import akka.actor.Actor
import akka.actor.Actor._
import can.{HttpConnection, Connect}
import akka.dispatch.{DefaultCompletableFuture, Future}
import HttpProtocols._

class HttpConduit(host: String, port: Int = 80, config: ConduitConfig = ConduitConfig.fromAkkaConf)
  extends MessagePipelining with Logging with SprayCanConversions {

  protected lazy val httpClient = ActorHelpers.actor(config.clientActorId)
  protected val mainActor = actorOf(new MainActor).start()

  val sendReceive: SendReceive = { request =>
    make(new DefaultCompletableFuture[HttpResponse](Long.MaxValue)) { future =>
      mainActor ! Send(request, { result => future.complete(result); () })
    }
  }

  def close() {
    mainActor.stop()
  }

  protected case class Send(request: HttpRequest, responder: Either[Throwable, HttpResponse] => Unit)
    extends HttpRequestContext

  protected class MainActor extends Actor {
    case class ConnectionResult(conn: Conn, send: Send, result: Either[Throwable, HttpConnection])
    case class Respond(conn: Conn, send: Send, result: Either[Throwable, HttpResponse])

    val conns = Seq.fill(config.maxConnections)(new Conn)

    protected def receive = {
      case send: Send => config.dispatchStrategy.dispatch(send, conns)
      case Respond(conn, Send(request, responder), result) =>
        log.debug("Dispatching '%s' response to %s", result.fold(_.toString, _.status.value), requestString(request), result)
        if (result.isLeft || closeExpected(result.right.get)) {
          conn.pendingResponses = -1
          conn.httpConnection = None
        } else conn.pendingResponses -= 1
        responder(result)
        config.dispatchStrategy.onStateChange(conns)
      case ConnectionResult(conn, send, Right(httpConnection)) =>
        conn.httpConnection = Some(Right(httpConnection))
        conn.pendingResponses -= 1 // correct for +1 in dispatch
        conn.dispatch(send)
      case ConnectionResult(conn, send, Left(error)) =>
        conn.httpConnection = None
        conn.pendingResponses = -1
        send.responder(Left(new PipelineException("Could not connect to %s:%s".format(host, port), error)))
    }

    def closeExpected(response: HttpResponse) = {
      import response._
      import HttpHeaders._
      protocol match {
        case `HTTP/1.0` => !headers.exists({ case Connection(Seq("Keep-Alive")) => true ; case _ => false })
        case `HTTP/1.1` => headers.exists({ case Connection(Seq("close")) => true ; case _ => false })
      }
    }

    def requestString(request: HttpRequest) = {
      "%s request to http://%s:%s%s".format(request.method, host, port, request.uri)
    }

    class Conn extends HttpConn {
      implicit val timeout = Actor.Timeout(Long.MaxValue) // in scope for '? Connect(...)' call below
      var pendingResponses: Int = -1
      var httpConnection: Option[Either[Future[HttpConnection], HttpConnection]] = None

      def dispatch(requestCtx: HttpRequestContext) {
        val send = requestCtx.asInstanceOf[Send]
        if (httpConnection.isEmpty) {
          log.debug("Opening new connection to %s:%s", host, port)
          pendingResponses = 1
          val connectionFuture = (httpClient ? Connect(host, port)).mapTo[HttpConnection].onComplete { future =>
            self ! ConnectionResult(this, send, future.value.get)
          }
          httpConnection = Some(Left(connectionFuture))
        } else {
          pendingResponses += 1
          def dispatchTo(connection: HttpConnection) {
            log.debug("Dispatching %s", requestString(send.request))
            connection.send(toSprayCanRequest(send.request)).onComplete { future =>
              self ! Respond(this, send, future.value.get.right.map(fromSprayCanResponse))
            }
          }
          httpConnection.get match {
            case Right(connection) => dispatchTo(connection)
            case Left(future) => future.onResult { case connection => dispatchTo(connection) }
          }
        }
      }
    }
  }
}