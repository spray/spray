package cc.spray

import akka.actor.ActorRef

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
  
  
}

object ServiceBuilder extends ServiceBuilder