package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._

trait XForwardedForHeader {
  this: Parser with AdditionalRules =>

  def X_FORWARDED_FOR = rule (
    oneOrMore(Ip, ListSep) ~ EOI
      ~~> (x => HttpHeaders.`X-Forwarded-For`(x: _*))
  )
  
}