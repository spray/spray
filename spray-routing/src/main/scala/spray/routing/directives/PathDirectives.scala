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

package spray.routing
package directives

import java.util.UUID
import scala.util.matching.Regex
import annotation.tailrec
import shapeless._
import spray.util._


trait PathDirectives extends PathMatcherImplicits with PathMatchers {
  import BasicDirectives.filter
  import PathMatcher._

  /**
   * Rejects the request if the unmatchedPath of the [[spray.routing.RequestContext]] is not matched by the
   * given PathMatcher. If matched the value extracted by the PathMatcher is passed on to the inner Route.
   */
  def path[L <: HList](pm: PathMatcher[L]): Directive[L] = pathPrefix(pm ~ PathEnd)

  /**
   * Returns a Route that rejects the request if the unmatchedPath of the [[spray.RequestContext]] does have a prefix
   * that matches the given PathMatcher.
   */
  def pathPrefix[L <: HList](pm: PathMatcher[L]): Directive[L] = {
    val matcher = Slash ~ pm
    filter { ctx =>
      matcher(ctx.unmatchedPath) match {
        case Matched(rest, values) => Pass(values, transform = _.copy(unmatchedPath = rest))
        case Unmatched => Reject.Empty
      }
    }
  }
}

object PathDirectives extends PathDirectives


/**
 * A PathMatcher tries to match a prefix of a given string and returns
 * 
 *  - None if not matched
 *  - Some(remainingPath, HList with the extracted values) if matched
 */
trait PathMatcher[L <: HList] extends (String => PathMatcher.Matching[L]) { self =>
  import PathMatcher._

  def / [R <: HList](other: PathMatcher[R])(implicit prepender: Prepender[L, R]) =
    this ~ PathMatchers.Slash ~ other

  def | (other: PathMatcher[L]) = new PathMatcher[L] {
    def apply(path: String) = self(path).orElse(other(path))
  }

  def ~ [R <: HList](other: PathMatcher[R])(implicit prepender: Prepender[L, R]): PathMatcher[prepender.Out] =
    mapMatching {
      case Matched(restL, valuesL) => other(restL).mapValues(prepender(valuesL, _))
      case Unmatched => Unmatched
    }

  def mapMatching[R <: HList](f: Matching[L] => Matching[R]) =
    new PathMatcher[R] { def apply(path: String) = f(self(path)) }

  def mapValues[R <: HList](f: L => R) = mapMatching(_.mapValues(f))

  def flatMapValues[R <: HList](f: L => Option[R]) = mapMatching(_.flatMapValues(f))
}

object PathMatcher extends PathMatcherImplicits {
  sealed trait Matching[+L <: HList] {
    def mapValues[R <: HList](f: L => R): Matching[R]
    def flatMapValues[R <: HList](f: L => Option[R]): Matching[R]
    def orElse[R >: L <: HList](other: => Matching[R]): Matching[R]
  }
  case class Matched[L <: HList](pathRest: String, extractions: L) extends Matching[L] {
    def mapValues[R <: HList](f: L => R) = Matched(pathRest, f(extractions))
    def flatMapValues[R <: HList](f: L => Option[R]) = f(extractions) match {
      case Some(valuesR) => Matched(pathRest, valuesR)
      case None => Unmatched
    }
    def orElse[R >: L <: HList](other: => Matching[R]) = this
  }
  object Matched { val Empty = Matched("", HNil) }
  case object Unmatched extends Matching[Nothing] {
    def mapValues[R <: HList](f: Nothing => R) = this
    def flatMapValues[R <: HList](f: Nothing => Option[R]) = this
    def orElse[R <: HList](other: => Matching[R]) = other
  }

  /**
   * Creates a [[spray.routing.directives.PathMatcher]] that extracts the given value if the given path prefix
   * can be matched.
   */
  def apply[T](prefix: String, value: T) = new PathMatcher[T :: HNil] {
    def apply(path: String) =
      if (path.startsWith(prefix)) Matched(path.substring(prefix.length), value :: HNil) else Unmatched
  }
}


trait PathMatcherImplicits {
  import PathMatcher._

  /**
   * A PathMatcher that matches the given string.
   */
  implicit def fromString(prefix: String) = new PathMatcher[HNil] {
    def apply(path: String) = if (path.startsWith(prefix)) Matched(path.substring(prefix.length), HNil) else Unmatched
  }

  /**
   * A PathMatcher that matches the given regular expression and either extracts the complete match (if the regex
   * doesn't contain a capture group) or the capture group (if the regex contains exactly one).
   * If the regex contains more than one capture group the method throws an IllegalArgumentException.
   */
  implicit def fromRegex(regex: Regex): PathMatcher[String :: HNil] = regex.groupCount match {
    case 0 => new PathMatcher[String :: HNil] {
      def apply(path: String) = regex.findPrefixOf(path) match {
        case Some(m) => Matched(path.substring(m.length), m :: HNil)
        case None => Unmatched
      }
    }
    case 1 => new PathMatcher[String :: HNil] {
      def apply(path: String) = regex.findPrefixMatchOf(path) match {
        case Some(m) => Matched(path.substring(m.end - m.start), m.group(1) :: HNil)
        case None => Unmatched
      }
    }
    case _ => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
            "' must not contain more than one capturing group")
  }
  /**
   * Creates a [[spray.directives.PathMatcher1]] from the given Map of path prefixes to extracted values.
   * If the unmatched path starts with one of the maps keys the matcher consumes this path prefix and extracts the
   * corresponding map value.
   */
  implicit def fromMap[T](valueMap: Map[String, T]): PathMatcher[T :: HNil] =
    valueMap.map { case (prefix, value) => PathMatcher(prefix, value) }.reduceLeft(_ | _)
}


trait PathMatchers {
  import PathMatcher._

  /**
   * A PathMatcher that matches a single slash character ('/').
   * Also matches at the very end of the requests URI path if no slash is present.
   */
  val Slash = new PathMatcher[HNil] {
    def apply(path: String) = {
      if (path.length == 0) Matched.Empty
      else if (path.length > 0 && path.charAt(0) == '/') Matched(path.substring(1), HNil)
      else Unmatched
    }
  }

  /**
   * A PathMatcher that matches the very end of the requests URI path.
   * Also matches if the only unmatched character left is a single slash.
   */
  val PathEnd = new PathMatcher[HNil] {
    def apply(path: String) =
      if (path.length == 0 || path.length == 1 && path.charAt(0) == '/') Matched.Empty else Unmatched
  }

  /**
   * A PathMatcher that matches and extracts the complete remaining, unmatched part of the requests URI path.
   */
  val Rest = new PathMatcher[String :: HNil] {
    def apply(path: String) = Matched("", path :: HNil)
  }

  /**
   * A PathMatcher that efficiently matches a number of digits and extracts their (non-negative) Int value.
   * The matcher will not match 0 digits or a sequence of digits that would represent an Int value larger
   * than Int.MaxValue.
   */
  object IntNumber extends NumberMatcher[Int](Int.MaxValue, 10) {
    def fromChar(c: Char) = fromDecimalChar(c)
  }

  /**
   * A PathMatcher that efficiently matches a number of digits and extracts their (non-negative) Long value.
   * The matcher will not match 0 digits or a sequence of digits that would represent an Long value larger
   * than Long.MaxValue.
   */
  object LongNumber extends NumberMatcher[Long](Long.MaxValue, 10) {
    def fromChar(c: Char) = fromDecimalChar(c)
  }

  /**
   * A PathMatcher that efficiently matches a number of hex-digits and extracts their (non-negative) Int value.
   * The matcher will not match 0 digits or a sequence of digits that would represent an Int value larger
   * than Int.MaxValue.
   */
  object HexIntNumber extends NumberMatcher[Int](Int.MaxValue, 16) {
    def fromChar(c: Char) = fromHexChar(c)
  }

  /**
   * A PathMatcher that efficiently matches a number of hex-digits and extracts their (non-negative) Long value.
   * The matcher will not match 0 digits or a sequence of digits that would represent an Long value larger
   * than Long.MaxValue.
   */
  object HexLongNumber extends NumberMatcher[Long](Long.MaxValue, 16) {
    def fromChar(c: Char) = fromHexChar(c)
  }

  // common implementation of Number matchers
  private[PathMatchers] abstract class NumberMatcher[@specialized(Int, Long) T](max: T, base: T)(implicit x: Integral[T])
          extends PathMatcher[T :: HNil] {
    import x._ // import implicit conversions for numeric operators
    val minusOne = x.zero - x.one
    val maxDivBase = max / base

    def apply(path: String) = digits(path, minusOne)

    @tailrec
    private def digits(remainingPath: String, value: T): Matching[T :: HNil] = {
      val a = if (remainingPath.isEmpty) minusOne else fromChar(remainingPath.charAt(0))
      if (a == minusOne) {
        if (value == minusOne) Unmatched
        else Matched(remainingPath, value :: HNil)
      } else {
        if (value == minusOne) digits(remainingPath.substring(1), a)
        else if (value <= maxDivBase && value * base <= max - a) { // protect from overflow
          digits(remainingPath.substring(1), value * base + a)
        } else Unmatched
      }
    }

    def fromChar(c: Char): T

    def fromDecimalChar(c: Char): T = if ('0' <= c && c <= '9') (c - '0').asInstanceOf[T] else minusOne

    def fromHexChar(c: Char): T = {
      if ('0' <= c && c <= '9') (c - '0').asInstanceOf[T] else
      if ('A' <= c && c <= 'F') (c - 'A' + 10).asInstanceOf[T] else
      if ('a' <= c && c <= 'f') (c - 'a' + 10).asInstanceOf[T] else
      minusOne
    }
  }

  /**
   * A PathMatcher that matches and extracts a Double value. The matched string representation is the pure decimal,
   * optionally signed form of a double value, i.e. without exponent.
   */
  val DoubleNumber = fromRegex("""[+-]?\d*\.?\d*""".r)
    .flatMapValues { case string :: HNil =>
      try Some(java.lang.Double.parseDouble(string) :: HNil)
      catch { case _: NumberFormatException => None }
    }

  /**
   * A PathMatcher that matches and extracts a java.util.UUID instance.
   */
  val JavaUUID = fromRegex("""[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r)
    .flatMapValues { case string :: HNil =>
      try Some(UUID.fromString(string) :: HNil)
      catch { case _: IllegalArgumentException => None }
    }

  /**
   * A PathMatcher that matches all characters except a slash '/'.
   * Equivalent to a regex matcher `"[^/]+".r` but more efficient.
   */
  val PathElement = new PathMatcher[String :: HNil] {
    def apply(path: String) = {
      @tailrec
      def chars(index: Int): Matching[String :: HNil] = {
        if (index == path.length || path.charAt(index) == '/')
          if (index > 0) Matched(path.substring(index), path.substring(0, index) :: HNil)
          else Unmatched
        else chars(index + 1)
      }
      chars(0)
    }
  }
}

object PathMatchers extends PathMatchers