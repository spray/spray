/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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

package spray.routing

import spray.http._
import MediaTypes._
import HttpHeaders._
import StatusCodes._

class RespondWithDirectivesSpec extends RoutingSpec {

  "respondWithStatus" should {
    "set the given status on successful responses" in {
      Get() ~> {
        respondWithStatus(Created) { completeOk }
      } ~> check { response === HttpResponse(Created) }
    }
    "leave rejections unaffected" in {
      Get() ~> {
        respondWithStatus(Created) { reject }
      } ~> check { rejections === Nil }
    }
  }

  "respondWithHeader" should {
    val customHeader = RawHeader("custom", "custom")
    "add the given headers to successful responses" in {
      Get() ~> {
        respondWithHeader(customHeader) { completeOk }
      } ~> check { response === HttpResponse(headers = customHeader :: Nil) }
    }
    "leave rejections unaffected" in {
      Get() ~> {
        respondWithHeader(customHeader) { reject }
      } ~> check { rejections === Nil }
    }
  }

  "The 'respondWithMediaType' directive" should {

    "override the media-type of its inner route response" in {
      Get() ~> {
        respondWithMediaType(`text/html`) {
          complete(<i>yeah</i>)
        }
      } ~> check { mediaType === `text/html` }
    }

    "disable content-negotiation for its inner marshaller" in {
      Get() ~> addHeader(Accept(`text/css`)) ~> {
        respondWithMediaType(`text/css`) {
          complete(<i>yeah</i>)
        }
      } ~> check { mediaType === `text/css` }
    }

    "reject an unacceptable request" in {
      Get() ~> addHeader(Accept(`text/css`)) ~> {
        respondWithMediaType(`text/xml`) {
          complete(<i>yeah</i>)
        }
      } ~> check { rejection === UnacceptedResponseContentTypeRejection(List(ContentType(`text/xml`))) }
    }
  }

}