/*
 * Copyright (C) 2011-2012 spray.io
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

import spray.http.StatusCodes.{ServerError, ClientError}


/**
 * Exception modelling all errors in an incoming HTTP request.
 * Apart from the response status code to generate for the error the exception also contains two
 * levels of informational messages. The summary should explain what is wrong with the request *without*
 * directly repeating anything present in the request (in order to not open holes for XSS attacks),
 * while the detail can contain additional information from any source (even the request itself).
 */
case class IllegalRequestException(status: ClientError, summary: String = "", detail: String = "")
  extends RuntimeException(if (summary.isEmpty) status.defaultMessage else summary + ": " + detail) {
  def this(status: ClientError, error: RequestErrorInfo) { this(status, error.summary, error.detail) }
}

case class RequestErrorInfo(summary: String = "", detail: String = "") {
  def withSummary(newSummary: String) = copy(summary = newSummary)
  def withFallbackSummary(fallbackSummary: String) = if (summary.isEmpty) withSummary(fallbackSummary) else this
  def formatPretty = summary + ": " + detail
}

object RequestErrorInfo {
  def apply(message: String): RequestErrorInfo  = message.split(": ", 2) match {
    case Array(summary, detail) => apply(summary, detail)
    case _ => RequestErrorInfo("", message)
  }
}


case class RequestProcessingException(status: ServerError, message: String = "")
  extends RuntimeException(if (message.isEmpty) status.defaultMessage else message)
