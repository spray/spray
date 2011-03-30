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

import util.matching.Regex
import utils.Product0

private[spray] trait PathBuilders {
  this: FilterBuilders =>

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does not match
   * the given PathMatcher. 
   */
  def path(pattern: PathMatcher0) = pathPrefix(pattern ~ PathEnd)

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does not match
   * the given PathMatcher. If it does match the value extracted by the matcher is passed to the inner Route building
   * function.
   */
  def path(pattern: PathMatcher1) = pathPrefix(pattern ~ PathEnd)

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path(pattern: PathMatcher2) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path(pattern: PathMatcher3) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path(pattern: PathMatcher4) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path(pattern: PathMatcher5) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does have a prefix
   * that matches the given PathMatcher.
   */
  def pathPrefix(pattern: PathMatcher0) = filter(pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the value extracted by the matcher is passed to the inner
   * Route building function.
   */
  def pathPrefix(pattern: PathMatcher1) = filter1[String](pathFilter(Slash ~ pattern))

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix(pattern: PathMatcher2) = filter2[String, String](pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix(pattern: PathMatcher3) = filter3[String, String, String](pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix(pattern: PathMatcher4) = filter4[String, String, String, String](pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix(pattern: PathMatcher5) = filter5[String, String, String, String, String](pathFilter(Slash ~ pattern))
  
  private def pathFilter[T <: Product](pattern: PathMatcher): RouteFilter[T] = { ctx =>
    pattern(ctx.unmatchedPath) match {
      case Some((remainingPath, captures)) => new Pass(captures.asInstanceOf[T], _.copy(unmatchedPath = remainingPath))
      case None => Reject()
    }
  } 
  
  // implicits
  
  implicit def string2Matcher(s: String): PathMatcher0 = new StringMatcher(s)
  
  implicit def regex2Matcher(regex: Regex): PathMatcher1 = {
    regex.groupCount match {
      case 0 => new SimpleRegexMatcher(regex)
      case 1 => new GroupRegexMatcher(regex)
      case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
              "' must not contain more than one capturing group")
    }
  }
  
}

/**
 * a PathMatcher tries to match a prefix of a given string and returns
 * - None if not matched
 * - Some(remainingPath, captures) if matched
 */
sealed trait PathMatcher extends (String => Option[(String, Product)])

sealed trait PathMatcher0 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def / (sub: PathMatcher3) = this ~ Slash ~ sub
  def / (sub: PathMatcher4) = this ~ Slash ~ sub
  def / (sub: PathMatcher5) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher0
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher1
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher2
  def ~ (sub: PathMatcher3) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher4) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher5) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher1 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def / (sub: PathMatcher3) = this ~ Slash ~ sub
  def / (sub: PathMatcher4) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher1
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher2
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher3) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher4) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher2 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def / (sub: PathMatcher3) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher2
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher3) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher3 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher4 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher5 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher5
}

private[spray] class Combi(a: PathMatcher, b: PathMatcher) {
  def apply(path: String) = a(path).flatMap {
    case (restA, capturesA) => b(restA).map {
      case (restB, capturesB) => (restB, capturesA productJoin capturesB) 
    }
  }
}

object Slash extends PathMatcher0 {
  private val Empty = Some(("", Product0)) // pre-allocated for speed 
  def apply(path: String) = {
    if (path.length == 0) {
      Empty
    } else if (path.length > 0 && path.charAt(0) == '/') {
      Some((path.substring(1), Product0))
    } else None
  }
}

object PathEnd extends PathMatcher0 {
  private val Empty = Some(("", Product0)) // pre-allocated for speed
  def apply(path: String) = {
    if (path.length == 0 || path.length == 1 && path.charAt(0) == '/') Empty else None
  }
}

object Remaining extends PathMatcher1 {
  def apply(path: String) = Some(("", Tuple1(path)))
}

private[builders] class StringMatcher(prefix: String) extends PathMatcher0 {
  def apply(path: String) = {
    if (path.startsWith(prefix)) Some((path.substring(prefix.length), Product0)) else None
  } 
}

private[builders] class SimpleRegexMatcher(regex: Regex) extends PathMatcher1 {
  def apply(path: String) = {
    regex.findPrefixOf(path).map(matched => (path.substring(matched.length), Tuple1(matched)))
  }
}

private[builders] class GroupRegexMatcher(regex: Regex) extends PathMatcher1 {
  def apply(path: String) = {
    regex.findPrefixMatchOf(path).map { m =>
      val matchLength = m.end - m.start
      (path.substring(matchLength), Tuple1(m.group(1)))
    }
  }
}