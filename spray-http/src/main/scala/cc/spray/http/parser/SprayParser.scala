/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.http.parser

import org.parboiled.scala._
import org.parboiled.errors.{ParserRuntimeException, ParsingException, ErrorUtils}

private[parser] trait SprayParser extends Parser {
  
  def parse[A](rule: Rule1[A], input: String): Either[String, A] = {
    try {
      val result = ReportingParseRunner(rule).run(input)
      result.result match {
        case Some(value) => Right(value)
        case None => Left(ErrorUtils.printParseErrors(result))
      }
    } catch {
      case e: ParserRuntimeException if e.getCause.isInstanceOf[ParsingException] =>
        Left(e.getCause.getMessage)
    }
  }
  
}