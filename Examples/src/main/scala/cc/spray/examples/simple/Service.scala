package cc.spray.examples.simple

import cc.spray._
import http._
import MimeObjects._

trait Service extends ServiceBuilder {
  
  def restService: Route =
    produces(`text/plain`) {
      handle { _.respond("Easy...") }
    }
  
}