/*
 * Copyright (C) 2011-2013 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

package spray.http

import scala.annotation.tailrec
import scala.collection.LinearSeq
import spray.http.parser.CharPredicate

trait Renderable {
  def render[R <: Rendering](r: R): R
}

trait ToStringRenderable extends Renderable {
  override def toString = render(new StringRendering).get
}

trait ValueRenderable extends ToStringRenderable {
  def value: String = toString
}

trait LazyValueBytesRenderable extends Renderable {
  // unsynchronized and non-volatile lazy init, worst case: we init once per core
  // which, since instances of derived classes are usually long-lived, is still better
  // that a synchronization overhead or even @volatile reads
  private[this] var _valueBytes: Array[Byte] = _
  private def valueBytes =
    if (_valueBytes != null) _valueBytes else { _valueBytes = asciiBytes(value); _valueBytes }

  def value: String
  def render[R <: Rendering](r: R): R = r ~~ valueBytes
  override def toString = value
}

trait SingletonValueRenderable extends Product with Renderable {
  private[this] val valueBytes = asciiBytes(value)
  def value = productPrefix
  def render[R <: Rendering](r: R): R = r ~~ valueBytes
}

trait Renderer[-T] {
  def render[R <: Rendering](r: R, value: T): R
}

object Renderer {
  implicit object CharRenderer extends Renderer[Char] {
    def render[R <: Rendering](r: R, value: Char): R = r ~~ value
  }
  implicit object StringRenderer extends Renderer[String] {
    def render[R <: Rendering](r: R, value: String): R = r ~~ value
  }
  implicit object BytesRenderer extends Renderer[Array[Byte]] {
    def render[R <: Rendering](r: R, value: Array[Byte]): R = r ~~ value
  }
  implicit object CharsRenderer extends Renderer[Array[Char]] {
    def render[R <: Rendering](r: R, value: Array[Char]): R = r ~~ value
  }
  object RenderableRenderer extends Renderer[Renderable] {
    def render[R <: Rendering](r: R, value: Renderable): R = value.render(r)
  }
  implicit def renderableRenderer[T <: Renderable]: Renderer[T] = RenderableRenderer

  def optionRenderer[D, T](defaultValue: D)(implicit sRenderer: Renderer[D], tRenderer: Renderer[T]) =
    new Renderer[Option[T]] {
      def render[R <: Rendering](r: R, value: Option[T]): R =
        if (value.isEmpty) sRenderer.render(r, defaultValue) else tRenderer.render(r, value.get)
    }

  def defaultSeqRenderer[T: Renderer] = seqRenderer[Renderable, T](Rendering.`, `)
  def seqRenderer[S, T](separator: S)(implicit sRenderer: Renderer[S], tRenderer: Renderer[T]) =
    new Renderer[Seq[T]] {
      def render[R <: Rendering](r: R, value: Seq[T]): R = {
        @tailrec def recI(values: IndexedSeq[T], ix: Int = 0): R =
          if (ix < values.size) {
            if (ix > 0) sRenderer.render(r, separator)
            tRenderer.render(r, values(ix))
            recI(values, ix + 1)
          } else r

        @tailrec def recL(remaining: LinearSeq[T]): R =
          if (remaining.nonEmpty) {
            if (remaining ne value) sRenderer.render(r, separator)
            tRenderer.render(r, remaining.head)
            recL(remaining.tail)
          } else r

        value match {
          case x: IndexedSeq[T] ⇒ recI(x)
          case x: LinearSeq[T]  ⇒ recL(x)
          case x                ⇒ sys.error("Unsupported Seq type: " + x)
        }
      }
    }
}

trait Rendering {
  def ~~(char: Char): this.type
  def ~~(bytes: Array[Byte]): this.type

  def ~~(int: Int): this.type = this ~~ int.toLong

  def ~~(long: Long): this.type =
    if (long != 0) {
      val value = if (long < 0) { this ~~ '-'; -long } else long
      @tailrec def magnitude(m: Long = 1): Long = if ((value / m) < 10) m else magnitude(m * 10)
      @tailrec def putNextChar(v: Long, m: Long): this.type =
        if (m > 0) {
          this ~~ ('0' + (v / m)).toChar
          putNextChar(v % m, m / 10)
        } else this
      putNextChar(value, magnitude())
    } else this ~~ '0'

  def ~~(string: String): this.type = {
    @tailrec def rec(ix: Int = 0): this.type =
      if (ix < string.length) { this ~~ string.charAt(ix); rec(ix + 1) } else this
    rec()
  }

  def ~~(chars: Array[Char]): this.type = {
    @tailrec def rec(ix: Int = 0): this.type =
      if (ix < chars.length) { this ~~ chars(ix); rec(ix + 1) } else this
    rec()
  }

  def ~~[T](value: T)(implicit ev: Renderer[T]): this.type = ev.render(this, value).asInstanceOf[this.type]

  def ~~#(s: String): this.type =
    if (CharPredicate.HttpToken.matchAll(s)) this ~~ s else ~~('"').putEscaped(s) ~~ '"'

  def putEscaped(s: String, escape: CharPredicate = Rendering.`\"`, escChar: Char = '\\'): this.type = {
    @tailrec def rec(ix: Int = 0): this.type =
      if (ix < s.length) {
        val c = s.charAt(ix)
        if (escape(c)) this ~~ escChar
        this ~~ c
        rec(ix + 1)
      } else this
    rec()
  }
}

object Rendering {
  val `\"` = CharPredicate('\\', '"')

  case object `, ` extends SingletonValueRenderable // default separator

  case object CrLf extends Renderable {
    def render[R <: Rendering](r: R): R = r ~~ '\r' ~~ '\n'
  }
}

class StringRendering extends Rendering {
  private[this] val sb = new java.lang.StringBuilder
  def ~~(char: Char): this.type = { sb.append(char); this }
  def ~~(bytes: Array[Byte]): this.type = { sb.append(new String(bytes, 0)); this }
  def get: String = sb.toString
}

class ByteArrayRendering(sizeHint: Int = 32) extends Rendering {
  import java.util.Arrays._
  private[this] var array = new Array[Byte](sizeHint)
  private[this] var cursor = 0
  def ~~(char: Char): this.type = put(char.toByte)
  def ~~(bytes: Array[Byte]): this.type = {
    if (cursor + bytes.length > array.length) array = copyOf(array, math.max(cursor + bytes.length, array.length * 2))
    System.arraycopy(bytes, 0, array, cursor, bytes.length)
    cursor += bytes.length
    this
  }
  def put(byte: Byte): this.type = {
    if (cursor == array.length) array = copyOf(array, cursor * 2)
    array(cursor) = byte
    cursor += 1
    this
  }
  def get: Array[Byte] = if (cursor == array.length) array else copyOfRange(array, 0, cursor)
}
