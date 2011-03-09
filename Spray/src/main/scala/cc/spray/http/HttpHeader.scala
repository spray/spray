package cc.spray
package http

import parser.HttpParser
import org.parboiled.scala.rules.Rule1

trait HttpHeader extends Product {
  val name = unmangle(productPrefix)
  def value: String
  override def toString = name + ": " + value
}

object HttpHeader {
  private var rules = Map.empty[String, Option[Rule1[HttpHeader]]]

  private def getRule(headerName: String): Option[Rule1[HttpHeader]] = {
    rules.get(headerName) match {
      case Some(ruleOption) => ruleOption
      case None => {
        val ruleOption = {
          try {
            val method = HttpParser.getClass.getMethod(headerName.trim.toUpperCase.replace('-', '_'))
            Some(method.invoke(HttpParser).asInstanceOf[Rule1[HttpHeader]])
          } catch {
            case _: NoSuchMethodException => None
          }
        }
        // unsynchronized write, we accept the small chance that we overwrite a just previously written value
        // and loose some cache efficiency, however we do save the cost of synchronization for all accesses
        rules = rules.updated(headerName, ruleOption)
        ruleOption
      }
    }
  }
  
  def apply(name: String, value: String): HttpHeader = {
    getRule(name) match {
      case None => HttpHeaders.CustomHeader(name, value)
      case Some(rule) => {
        HttpParser.parse(rule, value) match {
          case Left(error) => throw new HttpException(HttpStatusCodes.BadRequest, 
            "Illegal HTTP header '" + name + "':\n" + error)
          case Right(header) => header
        }
      } 
    }
  }
  
  def unapply(header: HttpHeader): Option[(String, String)] = Some(header.name -> header.value) 
}

object HttpHeaders {

  object Accept { def apply(first: MimeType, more: MimeType*): Accept = apply(first +: more) }
  case class Accept(mimeTypes: Seq[MimeType]) extends HttpHeader {
    def value = mimeTypes.mkString(", ")
  }
  
  object `Accept-Charset` { def apply(first: Charset, more: Charset*): `Accept-Charset` = apply(first +: more) }
  case class `Accept-Charset`(charsets: Seq[Charset]) extends HttpHeader {
    def value = charsets.mkString(", ")
  }
  
  object `Accept-Encoding` { def apply(first: Encoding, more: Encoding*): `Accept-Encoding` = apply(first +: more) }
  case class `Accept-Encoding`(encodings: Seq[Encoding]) extends HttpHeader {
    def value = encodings.mkString(", ")
  }
  
  object `Accept-Language` { def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more) }
  case class `Accept-Language`(languageRanges: Seq[LanguageRange]) extends HttpHeader {
    def value = languageRanges.mkString(", ")
  }
  
  object `Accept-Ranges` { def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more) }
  case class `Accept-Ranges`(rangeUnits: Seq[RangeUnit]) extends HttpHeader {
    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
  }
  
  case class `Content-Type`(mimeType: MimeType) extends HttpHeader {
    def value = mimeType.toString
  }
  
  object `X-Forwarded-For` { def apply(first: HttpIp, more: HttpIp*): `X-Forwarded-For` = apply(first +: more) }
  case class `X-Forwarded-For`(ips: Seq[HttpIp]) extends HttpHeader {
    def value = ips.mkString(", ")
  }
  
  case class `CustomHeader`(override val name: String, value: String) extends HttpHeader
}