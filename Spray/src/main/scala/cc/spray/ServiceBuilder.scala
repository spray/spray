package cc.spray

import akka.actor.ActorRef
import http._
import HttpHeaders._
import HttpMethods._

trait ServiceBuilder {  
  
  /*def split(r: Route[A, P]) {
    route { ctx => r(ctx); Unhandled }
  }
  
  def handle(actor: => ActorRef) {
    handle { actor ! _ }
  }

  def handle(f: Context[A, P] => Unit) {
    route { ctx => f(ctx); Handled } 
  }*/
  
  /*def get[A, P](f: Route[A, P]) {
    routes = routes :+ r
  }*/
  
  def accepts(mimeTypes: MimeType*) = filter { ctx =>
    ctx.request
            .extractHeader { case `Content-Type`(mimeType) => mimeType }
            .map(contentType => mimeTypes.exists(_.matches(contentType)))
            .getOrElse(false)
  } _

  def delete  = filter(_.request.method == DELETE) _
  def get     = filter(_.request.method == GET) _
  def head    = filter(_.request.method == HEAD) _
  def options = filter(_.request.method == OPTIONS) _
  def post    = filter(_.request.method == POST) _
  def put     = filter(_.request.method == PUT) _
  def trace   = filter(_.request.method == TRACE) _
  
  def methods(m: HttpMethod*) = filter(ctx => m.exists(_ == ctx.request.method)) _
  
  def filter(p: Context => Boolean)(route: Route): Route = { ctx => p(ctx) && route(ctx) }
  
  def produces(mimeType: MimeType)(route: Route): Route = { ctx =>
    route(ctx.withResponseHeader(`Content-Type`(mimeType)))
  }
  
  implicit def route2RouteConcatenation(route: Route): { def ~ (other: Route): Route } = new {
    def ~ (other: Route): Route = { ctx => route(ctx) || other(ctx) }
  }
  
}

object ServiceBuilder extends ServiceBuilder