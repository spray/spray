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

package cc.spray.can

import akka.util.Duration
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import akka.actor.{Actor, Scheduler}
import akka.dispatch.{DefaultCompletableFuture, Future}

private[can] trait HighLevelHttpClient {
  private lazy val log = LoggerFactory.getLogger(getClass)

  class HttpDialog[A](connectionF: Future[HttpConnection], resultF: Future[A]) {
    def send[B](request: HttpRequest)(implicit concat: (A, Future[HttpResponse]) => Future[B]): HttpDialog[B] = {
      appendToResultChain {
        val responseF = doSend(request)
        concat(_, responseF)
      }
    }

    def sendChunked[B](request: HttpRequest)(chunker: ChunkedRequester => Future[HttpResponse])
                      (implicit concat: (A, Future[HttpResponse]) => Future[B]): HttpDialog[B] = {
      appendToResultChain {
        val responseF = connectionF.flatMap { connection =>
          log.debug("Sending chunked request start {}", request)
          chunker(connection.startChunkedRequest(request))
        }
        concat(_, responseF)
      }
    }

    def reply(f: HttpResponse => HttpRequest)(implicit ev: A <:< HttpResponse): HttpDialog[HttpResponse] = {
      appendToResultChain(response => doSend(f(response)))
    }

    def awaitResponse: HttpDialog[A] = appendToConnectionChain { connection =>
      make(new DefaultCompletableFuture[HttpConnection](Long.MaxValue)) { nextConnectionF =>
        // only complete the next connection future once the result is in
        log.debug("Awaiting response")
        resultF.onComplete(_ => nextConnectionF.completeWithResult(connection))
      }
    }

    def waitIdle(duration: Duration): HttpDialog[A] = appendToConnectionChain { connection =>
      make(new DefaultCompletableFuture[HttpConnection](Long.MaxValue)) { nextConnectionF =>
        // delay completion of the next connection future by the given time
        val millis = duration.toMillis
        log.debug("Waiting {} ms", millis)
        Scheduler.scheduleOnce(() => nextConnectionF.completeWithResult(connection), millis, TimeUnit.MILLISECONDS)
      }
    }

    def end: Future[A] = resultF.onComplete { _ =>
      connectionF.onResult {
        case conn: HttpConnection => {
          log.debug("Closing connection after HttpDialog completion")
          conn.close()
        }
      }
    }

    private def appendToConnectionChain[B](f: HttpConnection => Future[HttpConnection]): HttpDialog[A] =
      new HttpDialog(connectionF.flatMap(f), resultF)

    private def appendToResultChain[B](f: A => Future[B]): HttpDialog[B] = {
      // map(identity) creates a fresh future, so onComplete listeners are invoked in order of registration
      new HttpDialog(connectionF.map(identity), resultF.flatMap(f))
    }

    private def doSend(request: HttpRequest): Future[HttpResponse] = connectionF.flatMap { connection =>
      log.debug("Sending request {}", request)
      connection.send(request)
    }
  }

  object HttpDialog {
    def apply(host: String, port: Int = 80,
              clientActorId: String = ClientConfig.fromAkkaConf.clientActorId): HttpDialog[Unit] = {
      implicit val timeout = Actor.Timeout(Long.MaxValue)
      val connection = (actor(clientActorId) ? Connect(host, port)).mapTo[HttpConnection]
      new HttpDialog(connection, connection.map(_ => ())) // start out with result type Unit
    }
  }

  implicit def concat(value: Unit, responseFuture: Future[HttpResponse]) = responseFuture
  implicit def concat(value: HttpResponse, responseFuture: Future[HttpResponse]) = responseFuture.map(Seq(value, _))
  implicit def concat(value: Seq[HttpResponse], responseFuture: Future[HttpResponse]) = responseFuture.map(value :+ _)
}