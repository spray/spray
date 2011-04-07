package cc.spray.builders

import annotation.tailrec

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
private[builders] abstract class NumberMatcher[@specialized(Int, Long) A](max: A, base: A)(implicit x: Integral[A])
        extends PathMatcher1[A] {
  import x._ // import implicit conversions for numeric operators
  val minusOne = x.zero - x.one
  val maxDivBase = max / base
  
  def apply(path: String) = {
    @tailrec
    def digits(remainingPath: String, value: A): Option[(String, Tuple1[A])] = {
      val a = if (remainingPath.isEmpty) minusOne else fromChar(remainingPath.charAt(0))
      if (a == minusOne) {
        if (value == minusOne) None else Some(remainingPath, Tuple1(value))
      } else {
        if (value == minusOne) {
          digits(remainingPath.substring(1), a)
        } else if (value <= maxDivBase && value * base <= max - a) { // protect from overflow
          digits(remainingPath.substring(1), value * base + a)
        } else None
      }
    }
    digits(path, minusOne)    
  }
  
  def fromChar(c: Char): A
  
  def fromDecimalChar(c: Char): A = if ('0' <= c && c <= '9') (c - '0').asInstanceOf[A] else minusOne
  
  def fromHexChar(c: Char): A = {
    if ('0' <= c && c <= '9') (c - '0').asInstanceOf[A] else
    if ('A' <= c && c <= 'F') (c - 'A' + 10).asInstanceOf[A] else
    if ('a' <= c && c <= 'f') (c - 'a' + 10).asInstanceOf[A] else
    minusOne
  }
}