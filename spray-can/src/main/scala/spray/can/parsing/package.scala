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

import akka.util.ByteString
import spray.util.SingletonException
import spray.http._

package object parsing {
  private[can]type Parser = ByteString ⇒ Result
}

package parsing {

  private[can] sealed trait Result
  private[can] object Result {
    case class NeedMoreData(next: Parser) extends Result
    case class Emit(part: HttpMessagePart, closeAfterResponseCompletion: Boolean, continue: () ⇒ Result) extends Result
    case class Expect100Continue(continue: () ⇒ Result) extends Result
    case class ParsingError(status: StatusCode, info: ErrorInfo) extends Result
    case object IgnoreAllFurtherInput extends Result with Parser { def apply(data: ByteString) = this }
  }

  class ParsingException(val status: StatusCode, val info: ErrorInfo) extends RuntimeException(info.formatPretty) {
    def this(status: StatusCode, summary: String = "") =
      this(status, ErrorInfo(if (summary.isEmpty) status.defaultMessage else summary))
    def this(summary: String) =
      this(StatusCodes.BadRequest, ErrorInfo(summary))
  }

  private object NotEnoughDataException extends SingletonException
}
