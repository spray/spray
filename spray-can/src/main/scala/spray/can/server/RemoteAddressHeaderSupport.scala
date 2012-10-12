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

package spray.can.server

import spray.io._
import spray.http._
import HttpHeaders._


object RemoteAddressHeaderSupport {

  def apply(): PipelineStage =
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val raHeader = `Remote-Address`(context.handle.remoteAddress.getAddress)
          def appendHeader(request: HttpRequest) = request.mapHeaders(raHeader :: _)

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case x: RequestParsing.HttpMessageStartEvent => eventPL {
              x.copy(
                messagePart = x.messagePart match {
                  case request: HttpRequest => appendHeader(request)
                  case ChunkedRequestStart(request) => ChunkedRequestStart(appendHeader(request))
                  case _ => throw new IllegalStateException
                }
              )
            }

            case ev => eventPL(ev)
          }
        }
    }
}