package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._
import java.net.InetAddress

// implementation of additional parsing rules required for extensions that are not in the core HTTP standard
trait AdditionalRules {
  this: Parser =>

  def Ip: Rule1[HttpIp] = rule (
    group(IpNumber ~ ch('.') ~ IpNumber ~ ch('.') ~ IpNumber ~ ch('.') ~ IpNumber)
      ~> (x => HttpIp(InetAddress.getByName(x))) ~ OptWS
  )
  
  def IpNumber = rule {
    Digit ~ optional(Digit ~ optional(Digit))
  }
}