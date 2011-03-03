package cc.spray

import akka.actor.ActorRef
import http._
import HttpHeaders._

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
  
  def produces(mimeType: MimeType)(route: Route): Route = { ctx =>
    route(ctx.withResponseHeader(`Content-Type`(mimeType)))
  }
  
  def handle(f: Context => Unit): Route = { ctx => f(ctx); Handled }
  
  implicit def route2ConcatRoute(route: Route): { def ~ (other: Route): Route } = new {
    def ~ (other: Route): Route = { ctx => route(ctx).handledOr(other(ctx)) }
  }
  
}

object ServiceBuilder extends ServiceBuilder