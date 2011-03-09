package cc.spray
package builders

import http._
import HttpMethods._
import HttpHeaders._
import akka.actor.Actor

private[spray] trait BasicBuilders {
  
  def accepts(mimeTypes: MimeType*) = filter { ctx =>
    (ctx.request.extractFromHeader { case `Content-Type`(mimeType) => mimeType }) match {
      case Some(contentType) => mimeTypes.exists(_.matches(contentType))
      case _ => false
    }
  } _

  def methods(m: HttpMethod*) = filter(ctx => m.exists(_ == ctx.request.method)) _
  
  def delete  = filter(_.request.method == DELETE) _
  def get     = filter(_.request.method == GET) _
  def head    = filter(_.request.method == HEAD) _
  def options = filter(_.request.method == OPTIONS) _
  def post    = filter(_.request.method == POST) _
  def put     = filter(_.request.method == PUT) _
  def trace   = filter(_.request.method == TRACE) _

  def filter(p: RequestContext => Boolean)(route: Route): Route = { ctx =>
    if (p(ctx)) route(ctx) else ctx.respondUnhandled
  }
  
  def produces(mimeType: MimeType) = responseHeader(`Content-Type`(mimeType)) _
  
  def responseHeader(header: HttpHeader)(route: Route): Route = { ctx =>
    route {
      ctx.withResponseTransformer { response =>
        Some(response.copy(headers = header :: response.headers.filterNot(_.name == header.name)))
      }
    }
  }
  
  def detached(route: Route)(implicit detachedActorFactory: Route => Actor): Route = { ctx =>
    Actor.actorOf(detachedActorFactory(route)).start ! ctx
  }
  
  
  // implicits
  
  implicit def route2RouteConcatenation(route: Route): { def ~ (other: Route): Route } = new {
    def ~ (other: Route): Route = { ctx =>
      route {
        ctx.withResponder { responseContext =>
          if (responseContext.response.isDefined) ctx.responder(responseContext) else other(ctx)
        }
      }
    }
  }
  
  implicit def defaultDetachedActorFactory(route: Route): Actor = new DetachedRouteActor(route)
  
}