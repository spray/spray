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
  extends Logging with SprayCanConversions {

  protected lazy val httpClient = ActorHelpers.actor(config.clientActorId)
  protected val mainActor = actorOf(new MainActor).start()

  def send[A, B](message: A)(implicit pipeline: HttpPipeline[A, B]): Future[B] = {
    make(new DefaultCompletableFuture[B](Long.MaxValue)) { future =>
      mainActor ! Send(
        request = pipeline.requestPipeline(message),
        responder = { result => future.complete(result.right.map(pipeline.responsePipeline)) }
      )
    }
  }

  def close() {
    mainActor.stop()
  }

  protected case class Send(request: HttpRequest, responder: Either[Throwable, HttpResponse] => Unit)
    extends HttpRequestContext

  protected class MainActor extends Actor {
    case class ConnectionResult(conn: Conn, send: Send, httpConnection: HttpConnection)
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
      case ConnectionResult(conn, send, httpConnection: HttpConnection) =>
        conn.pendingResponses = 0
        conn.httpConnection = Some(httpConnection)
        conn.dispatch(send)
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
      var httpConnection: Option[HttpConnection] = None

      def dispatch(requestCtx: HttpRequestContext) {
        (httpConnection, requestCtx) match {
          case (Some(connection), send: Send) =>
            log.debug("Dispatching %s", requestString(send.request))
            pendingResponses += 1
            connection.send(toSprayCanRequest(send.request)).onComplete { future =>
              self ! Respond(this, send, future.value.get.right.map(fromSprayCanResponse))
            }
          case (None, send: Send) => {
            log.debug("Opening new connection to %s:%s", host, port)
            (httpClient ? Connect(host, port)).mapTo[HttpConnection].onComplete {
              _.value.get match {
                case Right(connection) => self ! ConnectionResult(this, send, connection)
                case Left(error) => send.responder(Left(new ConnectionException(
                  "Could not connect to %s:%s".format(host, port), error
                )))
              }
            }
          }
        }
      }
    }
  }
}