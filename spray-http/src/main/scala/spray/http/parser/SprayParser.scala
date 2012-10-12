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

package spray.http.parser

import org.parboiled.scala._
import org.parboiled.errors.{ParserRuntimeException, ParsingException, ErrorUtils}
import spray.http.RequestErrorInfo


private[parser] trait SprayParser extends Parser {
  
  def parse[A](rule: Rule1[A], input: String): Either[RequestErrorInfo, A] = {
    try {
      val result = ReportingParseRunner(rule).run(input)
      result.result match {
        case Some(value) => Right(value)
        case None => Left(RequestErrorInfo(detail = ErrorUtils.printParseErrors(result)))
      }
    } catch {
      case e: ParserRuntimeException if e.getCause.isInstanceOf[ParsingException] =>
        Left(RequestErrorInfo(e.getCause.getMessage))
    }
  }
  
}