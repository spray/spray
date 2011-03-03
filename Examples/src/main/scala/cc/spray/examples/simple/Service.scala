package cc.spray.examples.simple

import cc.spray._
import http.HttpResponse

trait Service extends ServiceBuilder {
  
  def restService: Route = { ctx => ctx.respond(HttpResponse(content = Some("Great!".getBytes))); Handled }
  
}