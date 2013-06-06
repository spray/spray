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

package spray.testkit

import java.util.concurrent.{ TimeUnit, CountDownLatch }
import scala.collection.mutable.ListBuffer
import akka.actor.{ Status, ActorRefFactory, ActorRef }
import akka.spray.UnregisteredActorRef
import spray.routing.{ RejectionHandler, Rejected, Rejection }
import spray.http._
import akka.util.duration._
import akka.util.FiniteDuration

trait RouteResultComponent {

  def failTest(msg: String): Nothing

  /**
   * A receptacle for the response, rejections and potentially generated response chunks created by a route.
   */
  class RouteResult(timeout: FiniteDuration)(implicit actorRefFactory: ActorRefFactory) {
    private[this] var _response: Option[HttpResponse] = None
    private[this] var _rejections: Option[List[Rejection]] = None
    private[this] val _chunks = ListBuffer.empty[MessageChunk]
    private[this] var _closingExtension = ""
    private[this] var _trailer: List[HttpHeader] = Nil
    private[this] val latch = new CountDownLatch(1)
    private[this] var virginal = true

    private[testkit] val handler = new UnregisteredActorRef(actorRefFactory) {
      def handle(message: Any)(implicit sender: ActorRef) {
        def verifiedSender =
          if (sender != null) sender else sys.error("Received message " + message + " from unknown sender (null)")
        message match {
          case HttpMessagePartWrapper(x: HttpResponse, _) ⇒
            saveResult(Right(x))
            latch.countDown()
          case Rejected(rejections) ⇒
            saveResult(Left(rejections))
            latch.countDown()
          case HttpMessagePartWrapper(ChunkedResponseStart(x), ack) ⇒
            saveResult(Right(x))
            ack.foreach(verifiedSender.tell(_, this))
          case HttpMessagePartWrapper(x: MessageChunk, ack) ⇒
            synchronized { _chunks += x }
            ack.foreach(verifiedSender.tell(_, this))
          case HttpMessagePartWrapper(ChunkedMessageEnd(extension, trailer), _) ⇒
            synchronized { _closingExtension = extension; _trailer = trailer }
            latch.countDown()
          case Status.Failure(error) ⇒
            sys.error("Route produced exception: " + error)
          case x ⇒
            sys.error("Received invalid route response: " + x)
        }
      }
    }

    private[testkit] def awaitResult: this.type = {
      latch.await(timeout.toMillis, TimeUnit.MILLISECONDS)
      this
    }

    private def saveResult(result: Either[List[Rejection], HttpResponse]) {
      synchronized {
        if (!virginal) failTest("Route completed/rejected more than once")
        result match {
          case Right(resp) ⇒ _response = Some(resp)
          case Left(rejs)  ⇒ _rejections = Some(rejs)
        }
        virginal = false
      }
    }

    private def failNotCompletedNotRejected(): Nothing =
      failTest("Request was neither completed nor rejected within " + timeout)

    def handled: Boolean = synchronized { _response.isDefined }
    def response: HttpResponse = synchronized {
      _response.getOrElse {
        _rejections.foreach {
          RejectionHandler.applyTransformations(_) match {
            case Nil ⇒ failTest("Request was not handled")
            case r   ⇒ failTest("Request was rejected with " + r)
          }
        }
        failNotCompletedNotRejected()
      }
    }
    def rejections: List[Rejection] = synchronized {
      _rejections.getOrElse {
        _response.foreach(resp ⇒ failTest("Request was not rejected, response was " + resp))
        failNotCompletedNotRejected()
      }
    }
    def chunks: List[MessageChunk] = synchronized { _chunks.toList }
    def closingExtension = synchronized { _closingExtension }
    def trailer = synchronized { _trailer }

    def ~>[T](f: RouteResult ⇒ T): T = f(this)
  }

  case class RouteTestTimeout(duration: FiniteDuration)

  object RouteTestTimeout {
    implicit val default = RouteTestTimeout(1.second)
  }
}
