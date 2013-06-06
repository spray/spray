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

package spray.can.parsing

import akka.util.ByteString
import spray.util.SingletonException
import spray.http._

trait Parser[Part <: HttpMessagePart] extends (ByteString ⇒ Result[Part]) {
  def parse: ByteString ⇒ Result[Part]
}

sealed trait Result[+T <: HttpMessagePart]
object Result {
  case object NeedMoreData extends Result[Nothing]
  case class Ok[T <: HttpMessagePart](part: T, remainingData: ByteString,
                                      closeAfterResponseCompletion: Boolean) extends Result[T]
  case class Expect100Continue(remainingData: ByteString) extends Result[Nothing]
  case class ParsingError(status: StatusCode, info: ErrorInfo) extends Result[Nothing]
}

class ParsingException(val status: StatusCode, val info: ErrorInfo) extends RuntimeException(info.formatPretty) {
  def this(status: StatusCode, summary: String = "") =
    this(status, ErrorInfo(if (summary.isEmpty) status.defaultMessage else summary))
  def this(summary: String) =
    this(StatusCodes.BadRequest, ErrorInfo(summary))
}

object NotEnoughDataException extends SingletonException
