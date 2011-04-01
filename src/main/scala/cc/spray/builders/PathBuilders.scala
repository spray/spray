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
import annotation.tailrec

private[spray] trait PathBuilders {
  this: FilterBuilders =>

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does not match
   * the given PathMatcher. 
   */
  def path(pattern: PathMatcher0) = pathPrefix(pattern ~ PathEnd)

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does not match
   * the given PathMatcher. If it does match the value extracted by the matcher is passed to the inner Route building
   * function.
   */
  def path[A](pattern: PathMatcher1[A]) = pathPrefix(pattern ~ PathEnd)

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path[A, B](pattern: PathMatcher2[A, B]) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path[A, B, C](pattern: PathMatcher3[A, B, C]) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path[A, B, C, D](pattern: PathMatcher4[A, B, C, D]) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does not match
   * the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner Route building
   * function.
   */
  def path[A, B, C, D, E](pattern: PathMatcher5[A, B, C, D, E]) = pathPrefix(pattern ~ PathEnd)
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does have a prefix
   * that matches the given PathMatcher.
   */
  def pathPrefix(pattern: PathMatcher0) = filter(pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the value extracted by the matcher is passed to the inner
   * Route building function.
   */
  def pathPrefix[A](pattern: PathMatcher1[A]) = filter1[A](pathFilter(Slash ~ pattern))

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix[A, B](pattern: PathMatcher2[A, B]) = filter2[A, B](pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix[A, B, C](pattern: PathMatcher3[A, B, C]) = filter3[A, B, C](pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix[A, B, C, D](pattern: PathMatcher4[A, B, C, D]) = filter4[A, B, C, D](pathFilter(Slash ~ pattern))
  
  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[cc.spray.RequestContext]] does have a prefix
   * that matches the given PathMatcher. If it does match the values extracted by the matcher are passed to the inner
   * Route building function.
   */
  def pathPrefix[A, B, C, D, E](pattern: PathMatcher5[A, B, C, D, E]) = filter5[A, B, C, D, E](pathFilter(Slash ~ pattern))
  
  private def pathFilter[T <: Product](pattern: PathMatcher[T]): RouteFilter[T] = { ctx =>
    pattern(ctx.unmatchedPath) match {
      case Some((remainingPath, captures)) => new Pass(captures.asInstanceOf[T], _.copy(unmatchedPath = remainingPath))
      case None => Reject()
    }
  } 
  
  // implicits
  
  implicit def string2Matcher(s: String): PathMatcher0 = new StringMatcher(s)
  
  implicit def regex2Matcher(regex: Regex): PathMatcher1[String] = {
    regex.groupCount match {
      case 0 => new SimpleRegexMatcher(regex)
      case 1 => new GroupRegexMatcher(regex)
      case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
              "' must not contain more than one capturing group")
    }
  }
  
}

/**
 * A PathMatcher tries to match a prefix of a given string and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, TupleX with the value captures) if matched
 */
sealed trait PathMatcher[T <: Product] extends (String => Option[(String, T)])

/**
 * A PathMatcher0 extends a function that takes the unmatched path part of the request URI and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, [[cc.spray.utils.Product0]]) if matched (i.e. extracts nothing) 
 */
trait PathMatcher0 extends PathMatcher[Product0] {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / [A](sub: PathMatcher1[A]) = this ~ Slash ~ sub
  def / [A, B](sub: PathMatcher2[A, B]) = this ~ Slash ~ sub
  def / [A, B, C](sub: PathMatcher3[A, B, C]) = this ~ Slash ~ sub
  def / [A, B, C, D](sub: PathMatcher4[A, B, C, D]) = this ~ Slash ~ sub
  def / [A, B, C, D, E](sub: PathMatcher5[A, B, C, D, E]) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi[Product0](this, sub) with PathMatcher0
  def ~ [A](sub: PathMatcher1[A]) = new Combi[Tuple1[A]](this, sub) with PathMatcher1[A]
  def ~ [A, B](sub: PathMatcher2[A, B]) = new Combi[(A, B)](this, sub) with PathMatcher2[A, B]
  def ~ [A, B, C](sub: PathMatcher3[A, B, C]) = new Combi[(A, B, C)](this, sub) with PathMatcher3[A, B, C]
  def ~ [A, B, C, D](sub: PathMatcher4[A, B, C, D]) = new Combi[(A, B, C, D)](this, sub) with PathMatcher4[A, B, C, D]
  def ~ [A, B, C, D, E](sub: PathMatcher5[A, B, C, D, E]) = new Combi[(A, B, C, D, E)](this, sub) with PathMatcher5[A, B, C, D, E]
}

/**
 * A PathMatcher1[A] extends a function that takes the unmatched path part of the request URI and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, Tuple1(a)) if matched (with a being the extracted value) 
 */
trait PathMatcher1[A] extends PathMatcher[Tuple1[A]] {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / [B](sub: PathMatcher1[B]) = this ~ Slash ~ sub
  def / [B, C](sub: PathMatcher2[B, C]) = this ~ Slash ~ sub
  def / [B, C, D](sub: PathMatcher3[B, C, D]) = this ~ Slash ~ sub
  def / [B, C, D, E](sub: PathMatcher4[B, C, D, E]) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi[Tuple1[A]](this, sub) with PathMatcher1[A]
  def ~ [B](sub: PathMatcher1[B]) = new Combi[(A, B)](this, sub) with PathMatcher2[A, B]
  def ~ [B, C](sub: PathMatcher2[B, C]) = new Combi[(A, B, C)](this, sub) with PathMatcher3[A, B, C]
  def ~ [B, C, D](sub: PathMatcher3[B, C, D]) = new Combi[(A, B, C, D)](this, sub) with PathMatcher4[A, B, C, D]
  def ~ [B, C, D, E](sub: PathMatcher4[B, C, D, E]) = new Combi[(A, B, C, D, E)](this, sub) with PathMatcher5[A, B, C, D, E]
}

/**
 * A PathMatcher2[A, B] extends a function that takes the unmatched path part of the request URI and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, (a, b)) if matched (with a and b being the extracted values) 
 */
trait PathMatcher2[A, B] extends PathMatcher[(A, B)] {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / [C](sub: PathMatcher1[C]) = this ~ Slash ~ sub
  def / [C, D](sub: PathMatcher2[C, D]) = this ~ Slash ~ sub
  def / [C, D, E](sub: PathMatcher3[C, D, E]) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi[(A, B)](this, sub) with PathMatcher2[A, B]
  def ~ [C](sub: PathMatcher1[C]) = new Combi[(A, B, C)](this, sub) with PathMatcher3[A, B, C]
  def ~ [C, D](sub: PathMatcher2[C, D]) = new Combi[(A, B, C, D)](this, sub) with PathMatcher4[A, B, C, D]
  def ~ [C, D, E](sub: PathMatcher3[C, D, E]) = new Combi[(A, B, C, D, E)](this, sub) with PathMatcher5[A, B, C, D, E]
}

/**
 * A PathMatcher3[A, B, C] extends a function that takes the unmatched path part of the request URI and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, (a, b, c)) if matched (with a, b and c being the extracted values) 
 */
trait PathMatcher3[A, B, C] extends PathMatcher[(A, B, C)] {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / [D](sub: PathMatcher1[D]) = this ~ Slash ~ sub
  def / [D, E](sub: PathMatcher2[D, E]) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi[(A, B, C)](this, sub) with PathMatcher3[A, B, C]
  def ~ [D](sub: PathMatcher1[D]) = new Combi[(A, B, C, D)](this, sub) with PathMatcher4[A, B, C, D]
  def ~ [D, E](sub: PathMatcher2[D, E]) = new Combi[(A, B, C, D, E)](this, sub) with PathMatcher5[A, B, C, D, E]
}

/**
 * A PathMatcher4[A, B, C, D] extends a function that takes the unmatched path part of the request URI and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, (a, b, c, d)) if matched (with a, b, c and d being the extracted values) 
 */
trait PathMatcher4[A, B, C, D] extends PathMatcher[(A, B, C, D)] {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / [E](sub: PathMatcher1[E]) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi[(A, B, C, D)](this, sub) with PathMatcher4[A, B, C, D]
  def ~ [E](sub: PathMatcher1[E]) = new Combi[(A, B, C, D, E)](this, sub) with PathMatcher5[A, B, C, D, E]
}

/**
 * A PathMatcher5[A, B, C, D, E] extends a function that takes the unmatched path part of the request URI and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, (a, b, c, d, e)) if matched (with a, b, c, d and e being the extracted values) 
 */
trait PathMatcher5[A, B, C, D, E] extends PathMatcher[(A, B, C, D, E)] {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi[(A, B, C, D, E)](this, sub) with PathMatcher5[A, B, C, D, E]
}

private[spray] class Combi[T <: Product](a: PathMatcher[_ <: Product], b: PathMatcher[_ <: Product]) extends PathMatcher[T]{
  def apply(path: String) = a(path).flatMap {
    case (restA, capturesA) => b(restA).map {
      case (restB, capturesB) => (restB, (capturesA productJoin capturesB).asInstanceOf[T]) 
    }
  }
}

private[builders] class StringMatcher(prefix: String) extends PathMatcher0 {
  def apply(path: String) = {
    if (path.startsWith(prefix)) Some((path.substring(prefix.length), Product0)) else None
  } 
}

private[builders] class SimpleRegexMatcher(regex: Regex) extends PathMatcher1[String] {
  def apply(path: String) = {
    regex.findPrefixOf(path).map(matched => (path.substring(matched.length), Tuple1(matched)))
  }
}

private[builders] class GroupRegexMatcher(regex: Regex) extends PathMatcher1[String] {
  def apply(path: String) = {
    regex.findPrefixMatchOf(path).map { m =>
      val matchLength = m.end - m.start
      (path.substring(matchLength), Tuple1(m.group(1)))
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

object Remaining extends PathMatcher1[String] {
  def apply(path: String) = Some(("", Tuple1(path)))
}

/**
 * A PathMatcher that efficiently matches a number of digits and extracts their integer value.
 * The matcher will not match 0 digits or a sequence of digits that would represent an integer value larger
 * than Int.MaxValue.
 */
object INT extends PathMatcher1[Int] {
  def apply(path: String) = {
    @tailrec
    def swallowDigits(remainingPath: String, value: Int): Option[(String, Tuple1[Int])] = {
      val c = if (remainingPath.isEmpty) 'x' else remainingPath.charAt(0) - '0'
      if (0 <= c && c <= 9) {
        if (value < Int.MaxValue / 10) // protect from Int overflow
          swallowDigits(remainingPath.substring(1), if (value == -1) c else value * 10 + c)
        else None 
      } else {
        if (value == -1) None else Some(remainingPath, Tuple1(value))
      }
    }
    swallowDigits(path, -1)    
  }
} 