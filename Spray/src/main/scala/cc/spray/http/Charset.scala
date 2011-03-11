package cc.spray.http

import cc.spray.utils.ObjectRegistry
import java.nio.charset.{Charset => JCharset}

sealed trait Charset {
  def aliases: Seq[String]
  def value: String
  def nioCharset: JCharset
  override def toString = value
  
  def equalsOrIncludes(other: Charset): Boolean
}

// see http://www.iana.org/assignments/character-sets
object Charsets extends ObjectRegistry[String, Charset] {
  
  class StandardCharset private[Charsets] (val value: String, val aliases: String*) extends Charset {
    val nioCharset: JCharset = JCharset.forName(value)
    def equalsOrIncludes(other: Charset) = this eq other
    
    Charsets.register(this, value.toLowerCase)
    Charsets.register(this, aliases.map(_.toLowerCase))
  }
  
  case class CustomCharset(override val value: String) extends Charset {
    def aliases = List.empty[String]
    lazy val nioCharset: JCharset = JCharset.forName(value)
    def equalsOrIncludes(other: Charset) = this == other
  }
  
  val `*` = new Charset {
    def value = "*"
    def aliases = List.empty[String]
    def nioCharset = `ISO-8859-1`.nioCharset
    def equalsOrIncludes(other: Charset) = true
    
    Charsets.register(this, value)
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
