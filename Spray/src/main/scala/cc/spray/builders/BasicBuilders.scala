package cc.spray
package builders

import http._
import HttpMethods._
import HttpHeaders._
import akka.actor.Actor
import collection.mutable.WeakHashMap

private[spray] trait BasicBuilders {
  
  def accepts(mimeTypes: MimeType*) = filter { ctx =>
    (for (`Content-Type`(mimeType) <- ctx.request.headers) yield mimeType) match {
      case contentType :: Nil => mimeTypes.exists(_.matchesOrIncludes(contentType))
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
        Some(
          if (response.isSuccess) {
            response.copy(headers = header :: response.headers.filterNot(_.name == header.name))
          } else {
            response // in case of failures or warnings we do not set the header
          }
        )
      }
    }
  }
  
  def detached(route: Route)(implicit detachedActorFactory: Route => Actor): Route = { ctx =>
    Actor.actorOf(detachedActorFactory(route)).start ! ctx
  }
  
  private lazy val cache = WeakHashMap.empty[Any, HttpResponse]
  
  def cached(route: Route)(implicit keyer: RequestContext => CacheKey): Route = { ctx =>          
    val key = if (ctx.request.method == HttpMethods.GET) keyer(ctx) else DontCache
    if (key eq DontCache) {
      route(ctx)
    } else {
      def continueAndCacheResponse() = route {
        ctx.withResponseTransformer { response =>
          cache.update(key, response)
          Some(response)
        }
      }
      val cachedResponse = cache.get(key)
      if (cachedResponse.isDefined) {
        val responseType = cachedResponse.get.contentType
        if (responseType.isDefined && ctx.request.clientAccepts(responseType.get)) {
          ctx.respond(cachedResponse.get)
        } else continueAndCacheResponse()
      } else continueAndCacheResponse()
    }
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
  
  implicit def defaultCacheKeyer(ctx: RequestContext): CacheKey = CacheOn(ctx.request.uri)
  
}