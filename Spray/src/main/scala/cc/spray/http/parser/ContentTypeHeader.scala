package cc.spray.http
package parser

import org.parboiled.scala._
import HttpStatusCodes._
import HttpHeaders._

trait ContentTypeHeader {
  this: Parser with ProtocolParameterRules with CommonActions =>

  def CONTENT_TYPE = rule (
    MediaTypeDecl ~ EOI
  )
  
  def MediaTypeDecl = rule (
    MediaTypeDef ~~> (createContentTypeHeader(_, _, _))
  )
  
  private def createContentTypeHeader(mainType: String, subType: String, params: Map[String, String]) = {
    val mimeType = getMediaType(mainType, subType)
    params.get("charset").map { charsetName =>
      Charsets.get(charsetName.toLowerCase).getOrElse {
        throw new HttpException(BadRequest, "Unsupported charset: " + charsetName)
      }
    } match {
      case Some(charset) => `Content-Type`(ContentType(mimeType, charset)) 
      case None => `Content-Type`(ContentType(mimeType)) 
    }
  } 
  
}