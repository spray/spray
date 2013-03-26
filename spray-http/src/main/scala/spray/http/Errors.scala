/*
 * Copyright (C) 2011-2013 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

package spray.http

import spray.http.StatusCodes.{ ServerError, ClientError }

/**
 * Two-level model of error information.
 * The summary should explain what is wrong with the request or response *without* directly
 * repeating anything present in the message itself (in order to not open holes for XSS attacks),
 * while the detail can contain additional information from any source (even the request itself).
 */
case class ErrorInfo(summary: String = "", detail: String = "") {
  def withSummary(newSummary: String) = copy(summary = newSummary)
  def withFallbackSummary(fallbackSummary: String) = if (summary.isEmpty) withSummary(fallbackSummary) else this
  def formatPretty = summary + ": " + detail
  def format(withDetail: Boolean): String = if (withDetail) formatPretty else summary
}

object ErrorInfo {
  def apply(message: String): ErrorInfo = message.split(": ", 2) match {
    case Array(summary, detail) ⇒ apply(summary, detail)
    case _                      ⇒ ErrorInfo("", message)
  }
}

class IllegalUriException(info: ErrorInfo) extends RuntimeException(info.formatPretty) {
  def this(summary: String, detail: String = "") = this(ErrorInfo(summary, detail))
}

// the following exceptions are not used directly in spray-http
// but by the spray-routing and spray-servlet modules
// since the Client- and ServerError types are defined in spray-http the only
// commonly accessible place for this definition is here in spray-http

class IllegalRequestException private (val info: ErrorInfo, val status: ClientError)
    extends RuntimeException(info.formatPretty) {
  def this(status: ClientError) = this(ErrorInfo(status.defaultMessage), status)
  def this(status: ClientError, info: ErrorInfo) = this(info.withFallbackSummary(status.defaultMessage), status)
  def this(status: ClientError, detail: String) = this(ErrorInfo(status.defaultMessage, detail), status)
}

class RequestProcessingException private (val info: ErrorInfo, val status: ServerError)
    extends RuntimeException(info.formatPretty) {
  def this(status: ServerError) = this(ErrorInfo(status.defaultMessage), status)
  def this(status: ServerError, info: ErrorInfo) = this(info.withFallbackSummary(status.defaultMessage), status)
  def this(status: ServerError, detail: String) = this(ErrorInfo(status.defaultMessage, detail), status)
}