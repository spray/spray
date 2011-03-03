package cc.spray

import akka.actor.ActorRef
import http.HttpResponse

trait ServiceBuilder {  
  
  /*def split(r: Route[A, P]) {
    route { ctx => r(ctx); NotHandled }
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
  
  def handle(f: Context => Unit): Route = { ctx => f(ctx); Handled }

  implicit def byteArray2HttpResponse(array: Array[Byte]): HttpResponse = HttpResponse(content = Some(array))
  implicit def string2HttpResponse(s: String): HttpResponse = s.getBytes
  
}

object ServiceBuilder extends ServiceBuilder