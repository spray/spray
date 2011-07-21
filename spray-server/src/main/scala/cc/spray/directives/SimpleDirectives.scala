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
package directives

import http._
import HttpMethods._
import util.matching.Regex

private[spray] trait SimpleDirectives {
  this: BasicDirectives =>
  
  /**
   * A route filter that rejects all non-DELETE requests.
   */
  lazy val delete = method(DELETE)

  /**
   * A route filter that rejects all non-GET requests.
   */
  lazy val get = method(GET)

  /**
   * A route filter that rejects all non-HEAD requests.
   */
  lazy val head = method(HEAD)

  /**
   * A route filter that rejects all non-OPTIONS requests.
   */
  lazy val options = method(OPTIONS)

  /**
   * A route filter that rejects all non-POST requests.
   */
  lazy val post = method(POST)

  /**
   * A route filter that rejects all non-PUT requests.
   */
  lazy val put = method(PUT)

  /**
   * A route filter that rejects all non-TRACE requests.
   */
  lazy val trace = method(TRACE)

  /**
   * Returns a route filter that rejects all requests whose HTTP method does not match the given one.
   */
  def method(m: HttpMethod): SprayRoute0 = filter { ctx =>
    if (ctx.request.method == m) {
      Pass.withTransform(_.cancelRejections[MethodRejection])
    } else Reject(MethodRejection(m)) 
  }

  /**
   * Returns a route filter that rejects all requests with a host name different from the given one.
   */
  def host(hostName: String): SprayRoute0 = host(_ == hostName)

  /**
   * Returns a route filter that rejects all requests for whose host name the given predicate function return false.
   */
  def host(predicate: String => Boolean): SprayRoute0 = filter { ctx =>
    if (predicate(ctx.request.host)) Pass() else Reject()
  }

  /**
   * Returns a route filter that rejects all requests with a host name that does not have a prefix matching the given
   * regular expression. For all matching requests the prefix string matching the regex is extracted and passed to
   * the inner Route building function. If the regex contains a capturing group only the string matched by this group
   * is extracted. If the regex contains more than one capturing group an IllegalArgumentException will be thrown.
   */
  def host(regex: Regex): SprayRoute1[String] = filter1 { ctx =>
    def run(regexMatch: String => Option[String]) = {
      regexMatch(ctx.request.host) match {
        case Some(matched) => Pass(matched)
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
