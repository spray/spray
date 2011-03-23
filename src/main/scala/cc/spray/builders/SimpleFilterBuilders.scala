/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package builders

import http._
import HttpMethods._
import util.matching.Regex

private[spray] trait SimpleFilterBuilders {
  this: FilterBuilders =>
  
  def delete  = method(DELETE)
  def get     = method(GET)
  def head    = method(HEAD)
  def options = method(OPTIONS)
  def post    = method(POST)
  def put     = method(PUT)
  def trace   = method(TRACE)
  
  def method(m: HttpMethod): FilterRoute0[String] = filter { ctx =>
    if (ctx.request.method == m) Pass() else Reject(MethodRejection(m)) 
  }
  
  def host(hostName: String): FilterRoute0[String] = host(_ == hostName)
  
  def host(predicate: String => Boolean): FilterRoute0[String] = filter { ctx =>
    if (predicate(ctx.request.host)) Pass() else Reject()
  }
  
  def host(regex: Regex): FilterRoute1[String] = filter1 { ctx =>
    def run(regexMatch: String => Option[String]) = {
      regexMatch(ctx.request.host) match {
        case Some(matched) => Pass(matched :: Nil)
        case None => Reject()
      }
    }
    regex.groupCount match {
      case 0 => run(regex.findPrefixOf(_))
      case 1 => run(regex.findPrefixMatchOf(_).map(_.group(1)))
      case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
              "' must not contain more than one capturing group")
    }
  }
  
}
