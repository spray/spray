package cc.spray.http
package parser

import org.parboiled.scala._

trait ContentTypeHeader {
  this: Parser with ProtocolParameterRules with CommonActions =>

  def CONTENT_TYPE = rule (
    MediaTypeDef ~ EOI
      ~~> (HttpHeaders.`Content-Type`(_))
  )
  
  def MediaTypeDef = rule (
    MediaType ~~> (getMimeType(_, _))
  )
  
}