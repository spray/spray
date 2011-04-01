/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.http

import cc.spray.utils.ObjectRegistry
import java.nio.charset.{Charset => JCharset}

sealed trait CharsetRange {
  def value: String
  def matches(charset: Charset): Boolean
  override def toString = "CharsetRange(" + value + ')'
}

sealed trait Charset extends CharsetRange {
  def aliases: Seq[String]
  def nioCharset: JCharset
  def matches(charset: Charset) = value == charset.value
  override def toString = "Charset(" + value + ')'
}

// see http://www.iana.org/assignments/character-sets
object Charsets extends ObjectRegistry[String, Charset] {
  
  def register(charset: Charset) { register(charset, charset.value.toLowerCase) }
  
  val `*` = new CharsetRange {
    def value = "*"
    def matches(charset: Charset) = true
  }
  
  class StandardCharset private[Charsets] (val value: String, val aliases: String*) extends Charset {
    val nioCharset: JCharset = JCharset.forName(value)
    
    register(this)
    register(this, aliases.map(_.toLowerCase))
  }
  
  case class CustomCharset(value: String) extends Charset {
    def aliases = List.empty[String]
    lazy val nioCharset: JCharset = JCharset.forName(value)
  }
  
  val `US-ASCII`    = new StandardCharset("US-ASCII", "iso-ir-6", "ANSI_X3.4-1986", "ISO_646.irv:1991", "ASCII", "ISO646-US", "us", "IBM367", "cp367", "csASCII")
  val `ISO-8859-1`  = new StandardCharset("ISO-8859-1", "iso-ir-100", "ISO_8859-1", "latin1", "l1", "IBM819", "CP819", "csISOLatin1")
  val `ISO-8859-2`  = new StandardCharset("ISO-8859-2", "iso-ir-101", "ISO_8859-2", "latin2", "l2", "csISOLatin2") 
  val `ISO-8859-3`  = new StandardCharset("ISO-8859-3", "iso-ir-109", "ISO_8859-3", "latin3", "l3", "csISOLatin3")
  val `ISO-8859-4`  = new StandardCharset("ISO-8859-4", "iso-ir-110", "ISO_8859-4", "latin4", "l4", "csISOLatin4")
  val `ISO-8859-5`  = new StandardCharset("ISO-8859-5", "iso-ir-144", "ISO_8859-5", "cyrillic", "csISOLatinCyrillic")
  val `ISO-8859-6`  = new StandardCharset("ISO-8859-6", "iso-ir-127", "ISO_8859-6", "ECMA-114", "ASMO-708", "arabic", "csISOLatinArabic")
  val `ISO-8859-7`  = new StandardCharset("ISO-8859-7", "iso-ir-126", "ISO_8859-7", "ELOT_928", "ECMA-118", "greek", "greek8", "csISOLatinGreek")
  val `ISO-8859-8`  = new StandardCharset("ISO-8859-8", "iso-ir-138", "ISO_8859-8", "hebrew", "csISOLatinHebrew")
  val `ISO-8859-9`  = new StandardCharset("ISO-8859-9", "iso-ir-148", "ISO_8859-9", "latin5", "l5", "csISOLatin5")
  val `ISO-8859-10` = new StandardCharset("ISO-8859-1", "iso-ir-157", "l6", "ISO_8859-10", "csISOLatin6", "latin6")
  val `UTF-8`       = new StandardCharset("UTF-8", "UTF8")
  val `UTF-16`      = new StandardCharset("UTF-16", "UTF16")
  val `UTF-16BE`    = new StandardCharset("UTF-16BE")
  val `UTF-16LE`    = new StandardCharset("UTF-16LE")
  val `UTF-32`      = new StandardCharset("UTF-32", "UTF32")
  val `UTF-32BE`    = new StandardCharset("UTF-32BE")
  val `UTF-32LE`    = new StandardCharset("UTF-32LE")
}
