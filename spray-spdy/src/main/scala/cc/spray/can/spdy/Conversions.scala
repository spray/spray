package cc.spray.can.spdy

import java.util.zip.Inflater
import java.io.ByteArrayInputStream
import annotation.tailrec

object Conversions {
  def b2i(b1: Byte): Int = b1 & 0xff

  def u2be(b1: Byte, b2: Byte): Int =
    (b2i(b1) << 8) | b2i(b2)

  def u3be(b1: Byte, b2: Byte, b3: Byte): Int =
    (u2be(b1, b2) << 8) | b2i(b3)

  def u4be(b1: Byte, b2: Byte, b3: Byte, b4: Byte): Int =
    (u3be(b1, b2, b3) << 8) | b2i(b4)

  def u3le(b1: Byte, b2: Byte, b3: Byte): Int =
    (((b3 << 8) | b2) << 8) | b1

  case class Flag(mask: Int) {
    def apply(cand: Int): Boolean = (cand & mask) == mask
    def apply(set: Boolean): Int = if (set) mask else 0

    //def value(set: Boolean) = apply(set)
  }
  def flag(mask: Int): Flag = Flag(mask)
}