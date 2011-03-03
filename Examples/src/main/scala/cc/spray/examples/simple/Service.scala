package cc.spray.examples.simple

import cc.spray._
import http.HttpResponse

trait Service extends ServiceBuilder {
  
  def restService: Route = {//produces() {
    handle { _.respond("Easy...") }
  }
  
}