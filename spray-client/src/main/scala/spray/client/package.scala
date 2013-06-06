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

package spray

import akka.dispatch.Future
import spray.http._

package object client {
  type SendReceive = HttpRequest ⇒ Future[HttpResponse]
}

package client {

  class PipelineException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
  class UnsuccessfulResponseException(val responseStatus: StatusCode) extends RuntimeException

}
