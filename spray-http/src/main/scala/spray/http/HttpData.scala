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

import java.io.{ FileInputStream, File }
import java.nio.charset.Charset
import scala.collection.immutable.VectorBuilder
import scala.annotation.tailrec
import akka.util.ByteString
import spray.util.UTF8

sealed abstract class HttpData {
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty
  def length: Long
  def copyToArray(xs: Array[Byte], sourceOffset: Long = 0, targetOffset: Int = 0, span: Int = length.toInt): Unit
  def toByteArray: Array[Byte]
  def toByteString: ByteString
  def +:(other: HttpData): HttpData
  def asString: String = asString(UTF8)
  def asString(charset: HttpCharset): String = asString(charset.nioCharset)
  def asString(charset: java.nio.charset.Charset): String = new String(toByteArray, charset)
}

object HttpData {
  def apply(string: String): HttpData =
    apply(string, HttpCharsets.`UTF-8`)
  def apply(string: String, charset: HttpCharset): HttpData =
    if (string.isEmpty) Empty else new Bytes(ByteString(string getBytes charset.nioCharset))
  def apply(bytes: Array[Byte]): HttpData =
    if (bytes.isEmpty) Empty else new Bytes(ByteString(bytes))
  def apply(bytes: ByteString): HttpData =
    if (bytes.isEmpty) Empty else new Bytes(bytes)

  /**
   * Creates an HttpData.FileBytes instance if the given file exists, is readable, non-empty
   * and the given `length` parameter is non-zero. Otherwise the method returns HttpData.Empty.
   * A negative `length` value signifies that the respective number of bytes at the end of the
   * file is to be ommitted, i.e., a value of -10 will select all bytes starting at `offset`
   * except for the last 10.
   * If `length` is greater or equal to "file length - offset" all bytes in the file starting at
   * `offset` are selected.
   */
  def apply(file: File, offset: Long = 0, length: Long = Long.MaxValue): HttpData = {
    val fileLength = file.length
    if (fileLength > 0) {
      require(offset >= 0 && offset < fileLength, s"offset $offset out of range $fileLength")
      if (file.canRead)
        if (length > 0) new FileBytes(file.getAbsolutePath, offset, math.min(fileLength - offset, length))
        else if (length < 0 && length > offset - fileLength) new FileBytes(file.getAbsolutePath, offset, fileLength - offset + length)
        else Empty
      else Empty
    } else Empty
  }

  /**
   * Creates an HttpData.FileBytes instance if the given file exists, is readable, non-empty
   * and the given `length` parameter is non-zero. Otherwise the method returns HttpData.Empty.
   * A negative `length` value signifies that the respective number of bytes at the end of the
   * file is to be ommitted, i.e., a value of -10 will select all bytes starting at `offset`
   * except for the last 10.
   * If `length` is greater or equal to "file length - offset" all bytes in the file starting at
   * `offset` are selected.
   */
  def fromFile(fileName: String, offset: Long = 0, length: Long = Long.MaxValue) =
    apply(new File(fileName), offset, length)

  case object Empty extends HttpData {
    def isEmpty = true
    def length = 0L
    def copyToArray(xs: Array[Byte], sourceOffset: Long, targetOffset: Int, span: Int) = ()
    val toByteArray = Array.empty[Byte]
    def toByteString = ByteString.empty
    def +:(other: HttpData) = other
    override def asString(charset: Charset) = ""
  }

  sealed abstract class NonEmpty extends HttpData {
    def isEmpty = false
    def +:(other: HttpData): NonEmpty =
      other match {
        case Empty                                 ⇒ this
        case x: CompactNonEmpty                    ⇒ Compound(x, this)
        case Compound(head, tail: CompactNonEmpty) ⇒ Compound(head, Compound(tail, this))
        case x: Compound                           ⇒ newBuilder.+=(x).+=(this).result().asInstanceOf[Compound]
      }
    def toByteArray = {
      require(length <= Int.MaxValue, "Cannot create a byte array greater than 2GB")
      val array = Array.ofDim[Byte](length.toInt)
      copyToArray(array)
      array
    }
  }

  sealed abstract class CompactNonEmpty extends NonEmpty { _: Product ⇒
    override def toString = s"$productPrefix(<$length bytes>)"
  }

  case class Bytes private[HttpData] (bytes: ByteString) extends CompactNonEmpty {
    def length = bytes.length
    def copyToArray(xs: Array[Byte], sourceOffset: Long = 0, targetOffset: Int = 0, span: Int = length.toInt) = {
      require(sourceOffset >= 0, "sourceOffset must be >= 0 but is " + sourceOffset)
      if (sourceOffset < length)
        bytes.iterator.drop(sourceOffset.toInt).copyToArray(xs, targetOffset, span)
    }
    def toByteString = bytes
  }

  case class FileBytes private[HttpData] (fileName: String, offset: Long = 0, length: Long) extends CompactNonEmpty {
    def copyToArray(xs: Array[Byte], sourceOffset: Long = 0, targetOffset: Int = 0, span: Int = length.toInt) = {
      require(sourceOffset >= 0, "sourceOffset must be >= 0 but is " + sourceOffset)
      if (span > 0 && xs.length > 0 && sourceOffset < length) {
        require(0 <= targetOffset && targetOffset < xs.length, s"start must be >= 0 and <= ${xs.length} but is $targetOffset")
        val stream = new FileInputStream(fileName)
        try {
          stream.skip(offset + sourceOffset)
          val targetEnd = math.min(xs.length, targetOffset + math.min(span, (length - sourceOffset).toInt))
          @tailrec def load(ix: Int = targetOffset): Unit =
            if (ix < targetEnd)
              stream.read(xs, ix, targetEnd - ix) match {
                case -1    ⇒ // file length changed since this FileBytes instance was created
                case count ⇒ load(ix + count)
              }
          load()
        } finally stream.close()
      }
    }
    def toByteString = ByteString(toByteArray)
  }

  case class Compound private[HttpData] (head: CompactNonEmpty, tail: NonEmpty) extends NonEmpty {
    val length = head.length + tail.length
    def foreach(f: CompactNonEmpty ⇒ Unit): Unit = {
      @tailrec def rec(compound: Compound = this): Unit = {
        f(compound.head)
        compound.tail match {
          case x: CompactNonEmpty ⇒ f(x)
          case x: Compound        ⇒ rec(x)
        }
      }
      rec()
    }
    def copyToArray(xs: Array[Byte], sourceOffset: Long = 0, targetOffset: Int = 0, span: Int = length.toInt) = {
      require(sourceOffset >= 0, "sourceOffset must be >= 0 but is " + sourceOffset)
      if (span > 0 && xs.length > 0 && sourceOffset < length) {
        require(0 <= targetOffset && targetOffset < xs.length, s"start must be >= 0 and <= ${xs.length} but is $targetOffset")
        val targetEnd: Int = math.min(xs.length, targetOffset + math.min(span, (length - sourceOffset).toInt))
        var sCursor: Long = 0
        var tCursor: Int = targetOffset
        foreach { current ⇒
          val nextSCursor: Long = sCursor + current.length
          if (tCursor < targetEnd && nextSCursor > sourceOffset) {
            val sOffset = -math.min(sCursor - sourceOffset, 0)
            current.copyToArray(xs,
              sourceOffset = sOffset,
              targetOffset = tCursor,
              span = targetEnd - tCursor)
            tCursor = math.min(tCursor + current.length - sOffset, Int.MaxValue).toInt
          }
          sCursor = nextSCursor
        }
      }
    }
    override def toString = head.toString + " +: " + tail
    def toByteString = ByteString(toByteArray)
  }

  def newBuilder: Builder = new Builder

  class Builder extends scala.collection.mutable.Builder[HttpData, HttpData] {
    private val b = new VectorBuilder[CompactNonEmpty]
    private var _byteCount = 0L

    def byteCount: Long = _byteCount

    def +=(x: CompactNonEmpty): this.type = {
      b += x
      _byteCount += x.length
      this
    }

    def +=(elem: HttpData): this.type =
      elem match {
        case Empty              ⇒ this
        case x: CompactNonEmpty ⇒ this += x
        case x: Compound ⇒
          @tailrec def append(current: NonEmpty): this.type =
            current match {
              case x: CompactNonEmpty   ⇒ this += x
              case Compound(head, tail) ⇒ this += head; append(tail)
            }
          append(x)
      }

    def clear(): Unit = b.clear()

    def result(): HttpData =
      b.result().foldRight(Empty: HttpData) {
        case (x, Empty)          ⇒ x
        case (x, tail: NonEmpty) ⇒ Compound(x, tail)
      }
  }
}