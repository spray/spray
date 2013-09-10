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

package spray.routing
package directives

import shapeless._

trait PathDirectives extends PathMatchers with ImplicitPathMatcherConstruction {
  import BasicDirectives._
  import RouteDirectives._
  import PathMatcher._

  /**
   * Rejects the request if the unmatchedPath of the [[spray.routing.RequestContext]] is not matched by the
   * given PathMatcher. If matched the value extracted by the PathMatcher is extracted.
   */
  def path[L <: HList](pm: PathMatcher[L]): Directive[L] = pathPrefix(pm ~ PathEnd)

  /**
   * Rejects the request if the unmatchedPath of the [[spray.RequestContext]] does not have a prefix
   * matched the given PathMatcher. If matched the value extracted by the PathMatcher is extracted
   * and the matched parts of the path are consumed.
   */
  def pathPrefix[L <: HList](pm: PathMatcher[L]): Directive[L] = {
    val matcher = Slash ~ pm
    extract(ctx ⇒ matcher(ctx.unmatchedPath)).flatMap {
      case Matched(rest, values) ⇒ hprovide(values) & mapRequestContext(_.copy(unmatchedPath = rest))
      case Unmatched             ⇒ reject
    }
  }

  /**
   * Checks whether the unmatchedPath of the [[spray.RequestContext]] has a prefix matched by the
   * given PathMatcher. However, as opposed to the pathPrefix directive the matched path is not
   * actually "consumed".
   */
  def pathPrefixTest[L <: HList](pm: PathMatcher[L]): Directive[L] = {
    val matcher = Slash ~ pm
    extract(ctx ⇒ matcher(ctx.unmatchedPath)).flatMap {
      case Matched(_, values) ⇒ hprovide(values)
      case Unmatched          ⇒ reject
    }
  }

  /**
   * Rejects the request if the unmatchedPath of the [[spray.RequestContext]] does not have a suffix
   * matched the given PathMatcher. If matched the value extracted by the PathMatcher is extracted
   * and the matched parts of the path are consumed.
   * Note that, if the given PathMatcher is a compound one consisting of several concatenated sub-matchers,
   * the order of the sub-matchers in the concatenation has to be reversed!
   */
  def pathSuffix[L <: HList](pm: PathMatcher[L]): Directive[L] =
    extract(ctx ⇒ pm(ctx.unmatchedPath.reverse)).flatMap {
      case Matched(rest, values) ⇒ hprovide(values) & mapRequestContext(_.copy(unmatchedPath = rest.reverse))
      case Unmatched             ⇒ reject
    }

  /**
   * Checks whether the unmatchedPath of the [[spray.RequestContext]] has a suffix matched by the
   * given PathMatcher. However, as opposed to the pathSuffix directive the matched path is not
   * actually "consumed".
   */
  def pathSuffixTest[L <: HList](pm: PathMatcher[L]): Directive[L] =
    extract(ctx ⇒ pm(ctx.unmatchedPath.reverse)).flatMap {
      case Matched(_, values) ⇒ hprovide(values)
      case Unmatched          ⇒ reject
    }
}

object PathDirectives extends PathDirectives