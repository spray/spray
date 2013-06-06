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

package spray.client

import akka.util.duration._
import akka.dispatch.{ ExecutionContext, Promise, Future }
import akka.pattern.ask
import akka.util.{ FiniteDuration, Timeout }
import akka.io.IO
import akka.actor._
import spray.can.client.ClientConnectionSettings
import spray.can.Http
import spray.util._
import spray.http._

/**
 * An `HttpDialog` allows you to define an exchange of HTTP messages over a given transport
 * (i.e. at the `HttpClientConnection`, `HttpHostConnector` or `HttpClient` level).
 * It provides a fluent API for constructing a "chain" of scheduled tasks that define what
 * to do over the course of the dialog.
 */
object HttpDialog {

  def apply(connect: Http.Connect)(implicit refFactory: ActorRefFactory, ec: ExecutionContext): Dialog#State0 = {
    val trigger = Promise[Unit]()
    val transport = refFactory.actorOf {
      Props {
        new Actor {
          import context.system
          IO(Http) ! connect
          def receive = {
            case _: Http.Connected ⇒
              trigger.success(())
              context.become(connected(context.watch(sender)))
            case _: Http.CommandFailed ⇒
              trigger.failure(new RuntimeException("Could not connect to " + connect.remoteAddress))
              context.stop(self)
          }
          def connected(connection: ActorRef): Receive = {
            case Terminated(`connection`) ⇒ context.stop(self)
            case x                        ⇒ connection.forward(x)
          }
        }
      }
    }
    val settings = ClientConnectionSettings(connect.settings)
    implicit val timeout = Timeout {
      settings.requestTimeout match {
        case x: FiniteDuration ⇒ x + 1.second
        case _                 ⇒ 60.seconds
      }
    }
    val dialog = new Dialog(transport)
    new dialog.State0(trigger.future)
  }

  def apply(transport: ActorRef)(implicit refFactory: ActorRefFactory, ec: ExecutionContext,
                                 requestTimeout: Timeout = 30.seconds): Dialog#State0 = {
    val dialog = new Dialog(transport)
    new dialog.State0(Promise.successful(()).future)
  }

  class Dialog private[HttpDialog] (transport: ActorRef)(implicit refFactory: ActorRefFactory,
                                                         ec: ExecutionContext, requestTimeout: Timeout) {

    class State0(trigger: Future[Unit]) {
      def send(request: HttpRequest) = new State1(trigger, responseFor(request, trigger))
      def send(requests: Seq[HttpRequest]) = new StateN(trigger, responsesFor(requests, trigger))
      def waitIdle(duration: FiniteDuration) = new State0(trigger.delay(duration))
    }

    class State1(trigger: Future[Unit], target: Future[HttpResponse]) {
      def send(request: HttpRequest) = stateN(firstResp ⇒ responseFor(request, trigger).map(firstResp :: _ :: Nil))
      def send(requests: Seq[HttpRequest]) = stateN(firstResp ⇒ responsesFor(requests, trigger).map(firstResp +: _))
      def reply(f: HttpResponse ⇒ HttpRequest) = {
        val newTarget = target.flatMap(response ⇒ responseFor(f(response)))
        new State1(newTarget.map(_ ⇒ ()), newTarget)
      }
      def awaitResponse = new State1(target.map(_ ⇒ ()), target)
      def waitIdle(duration: FiniteDuration) = new State1(trigger.delay(duration), target)
      def end = target
      private def stateN(f: HttpResponse ⇒ Future[Seq[HttpResponse]]) = new StateN(trigger, target.flatMap(f))
    }

    class StateN(trigger: Future[Unit], target: Future[Seq[HttpResponse]]) {
      def send(request: HttpRequest) = stateN(responses ⇒ responseFor(request, trigger).map(responses :+ _))
      def send(requests: Seq[HttpRequest]) = stateN(responses ⇒ responsesFor(requests, trigger).map(responses ++ _))
      def awaitAllResponses = new StateN(target.map(_ ⇒ ()), target)
      def waitIdle(duration: FiniteDuration) = new StateN(trigger.delay(duration), target)
      def end = target
      private def stateN(f: Seq[HttpResponse] ⇒ Future[Seq[HttpResponse]]) = new StateN(trigger, target.flatMap(f))
    }

    private def responsesFor(requests: Seq[HttpRequest], trigger: Future[Unit]): Future[Seq[HttpResponse]] =
      trigger.flatMap(_ ⇒ Future.sequence(requests.map(responseFor)))

    private def responseFor(request: HttpRequest, trigger: Future[Unit]): Future[HttpResponse] =
      trigger.flatMap(_ ⇒ responseFor(request))

    private def responseFor(request: HttpRequest): Future[HttpResponse] =
      transport.ask(request).mapTo[HttpResponse]
  }

}
