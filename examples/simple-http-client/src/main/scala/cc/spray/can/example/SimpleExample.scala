/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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
package example

import cc.spray.io.IoWorker
import akka.actor.{Props, ActorSystem}
import model.HttpRequest


object SimpleExample extends App {
  implicit val system = ActorSystem()
  def log = system.log

  // every spray-can HttpClient (and HttpServer) needs an IoWorker for low-level network IO
  // (but several servers and/or clients can share one)
  val ioWorker = new IoWorker(system).start()

  // create and start the spray-can HttpClient
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioWorker)),
    name = "http-client"
  )

  // create a very basic HttpDialog that results in a Future[HttpResponse]
  log.info("Dispatching GET request to github.com")
  val responseF =
    HttpDialog(httpClient, "github.com")
      .send(HttpRequest(uri = "/"))
      .end

  // "hook in" our continuation
  responseF.onComplete { result =>
    result match {
      case Right(response) =>
        log.info(
          """|Result from host:
             |status : {}
             |headers: {}
             |body   : {}""".stripMargin,
          response.status, response.headers.mkString("\n  ", "\n  ", ""), response.bodyAsString
        )
      case Left(error) =>
        log.error("Could not get response due to {}", error)
    }

    log.info("Shutting down...")
    // always cleanup
    system.shutdown()
    ioWorker.stop()
  }
}
