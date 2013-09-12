/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

package spray.can
package parsing

import spray.can.server.RequestParsing
import spray.io._
import spray.http.HttpMessageStart
import spray.http.HttpHeaders.`SSL-Session-Info`
import spray.util.SSLSessionInfo

/** Pipeline stage that adds the `SSL-Session-Info` header to incoming HTTP messages. */
object SSLSessionInfoSupport extends PipelineStage {

  def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
    new Pipelines {
      var sslSessionInfo: Option[`SSL-Session-Info`] = None

      def addSessionInfoHeader(message: HttpMessageStart): HttpMessageStart =
        sslSessionInfo.fold(message) { info ⇒ message.mapHeaders { hdrs ⇒ info :: hdrs } }

      val commandPipeline: CPL = commandPL

      val eventPipeline: EPL = {
        case SslTlsSupport.SSLSessionEstablished(info) ⇒
          sslSessionInfo = Some(`SSL-Session-Info`(info))

        case Http.MessageEvent(msg: HttpMessageStart) if sslSessionInfo.isDefined ⇒
          eventPL(Http.MessageEvent(addSessionInfoHeader(msg)))

        case RequestParsing.HttpMessageStartEvent(part, closeAfterResponseCompletion) if sslSessionInfo.isDefined ⇒
          eventPL(RequestParsing.HttpMessageStartEvent(addSessionInfoHeader(part), closeAfterResponseCompletion))

        case ev ⇒ eventPL(ev)
      }
    }
}
