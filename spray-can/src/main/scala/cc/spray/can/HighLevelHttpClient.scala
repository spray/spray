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

import akka.dispatch.{DefaultCompletableFuture, Future}
import akka.util.Duration
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import akka.actor.{Actor, Scheduler, ActorRef}

private[can] trait HighLevelHttpClient {
  private lazy val log = LoggerFactory.getLogger(getClass)

  class HttpDialog[A](client: ActorRef, connectionF: Future[ConnectionHandle], resultF: Future[A]) {
    def send[B](request: HttpRequest)(implicit concat: (A, Future[HttpResponse]) => Future[B]): HttpDialog[B] = {
      appendToResultChain(concat(_, doSend(request)))
    }

    def reply(f: HttpResponse => HttpRequest)(implicit ev: A <:< HttpResponse): HttpDialog[HttpResponse] = {
      appendToResultChain(response => doSend(f(response)))
    }

    def awaitResponse: HttpDialog[A] = appendToConnectionChain { connection =>
      make(new DefaultCompletableFuture[ConnectionHandle](Long.MaxValue)) { nextConnectionF =>
        // only complete the next connection future once the result is in
        log.debug("Awaiting response")
        resultF.onComplete(_ => nextConnectionF.completeWithResult(connection))
      }
    }

    def waitIdle(duration: Duration): HttpDialog[A] = appendToConnectionChain { connection =>
      make(new DefaultCompletableFuture[ConnectionHandle](Long.MaxValue)) { nextConnectionF =>
        // delay completion of the next connection future by the given time
        val millis = duration.toMillis
        log.debug("Waiting {} ms", millis)
        Scheduler.scheduleOnce(() => nextConnectionF.completeWithResult(connection), millis, TimeUnit.MILLISECONDS)
      }
    }

    def end: Future[A] = resultF.onComplete { _ =>
      connectionF.onResult {
        case conn: ConnectionHandle => {
          log.debug("Closing connection after HttpDialog completion")
          client ! Close(conn)
        }
      }
    }

    private def appendToConnectionChain[B](f: ConnectionHandle => Future[ConnectionHandle]): HttpDialog[A] =
      new HttpDialog(client, connectionF.flatMap(f), resultF)

    private def appendToResultChain[B](f: A => Future[B]): HttpDialog[B] =
      new HttpDialog(client, connectionF, resultF.flatMap(f))

    private def doSend(request: HttpRequest): Future[HttpResponse] = connectionF.flatMap { connection =>
      log.debug("Sending request {}", request)
      implicit val timeout = Actor.Timeout(Long.MaxValue)
      (client ? Send(connection, request)).mapTo[Received].map {
        case Received(Right(response)) => response
        case Received(Left(error)) => throw new RuntimeException(error) // unwrap error into Future
      }
    }
  }

  object HttpDialog {
    private lazy val log = LoggerFactory.getLogger(getClass)

    def apply(host: String, port: Int = 80, clientActorId: String = ClientConfig.fromAkkaConf.clientActorId): HttpDialog[Unit] = {
      val client = actor(clientActorId)
      val connection = new DefaultCompletableFuture[ConnectionHandle](Long.MaxValue)
      implicit val timeout = Actor.Timeout(Long.MaxValue)
      val result: Future[Unit] = (client ? Connect(host, port)).mapTo[ConnectionResult].map {
        case ConnectionResult(Right(connectionHandle)) => {
          log.debug("Connected to {}", host + ":" + port)
          connection.completeWithResult(connectionHandle)
        }
        case ConnectionResult(Left(error)) => {
          val exception = new RuntimeException(error)
          connection.complete(Left(exception))
          throw exception // unwrap error into Future
        }
      }
      new HttpDialog(client, connection, result)
    }
  }

  implicit def concat(value: Unit, responseFuture: Future[HttpResponse]) = responseFuture
  implicit def concat(value: HttpResponse, responseFuture: Future[HttpResponse]) = responseFuture.map(Seq(value, _))
  implicit def concat(value: Seq[HttpResponse], responseFuture: Future[HttpResponse]) = responseFuture.map(value :+ _)
}