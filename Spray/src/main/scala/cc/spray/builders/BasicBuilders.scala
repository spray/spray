package cc.spray
package builders

import http._
import HttpMethods._
import HttpHeaders._
import akka.actor.Actor
import collection.mutable.WeakHashMap

private[spray] trait BasicBuilders {
  
  def accepts(mimeTypes: MimeType*) = filter(AcceptRejection) { ctx =>
    (for (`Content-Type`(mimeType) <- ctx.request.headers) yield mimeType) match {
      case contentType :: Nil => mimeTypes.exists(_.matchesOrIncludes(contentType))
      case _ => false
    }
  } _

  def methods(m: HttpMethod*) = filter(MethodRejection) { ctx => m.exists(_ == ctx.request.method) } _
  
  def delete  = filter(MethodRejection) { _.request.method == DELETE } _
  def get     = filter(MethodRejection) { _.request.method == GET } _
  def head    = filter(MethodRejection) { _.request.method == HEAD } _
  def options = filter(MethodRejection) { _.request.method == OPTIONS } _
  def post    = filter(MethodRejection) { _.request.method == POST } _
  def put     = filter(MethodRejection) { _.request.method == PUT } _
  def trace   = filter(MethodRejection) { _.request.method == TRACE } _

  def filter(rejection: Rejection)(p: RequestContext => Boolean)(route: Route): Route = { ctx =>
    if (p(ctx)) route(ctx) else ctx.reject(rejection)
  }
  
  def produces(mimeType: MimeType) = responseHeader(`Content-Type`(mimeType)) _
  
  def responseHeader(header: HttpHeader)(route: Route): Route = { ctx =>
    route {
      ctx.withHttpResponseTransformed { response => 
        if (response.isSuccess) {
          response.copy(headers = header :: response.headers.filterNot(_.name == header.name))
        } else {
          response // in case of failures or warnings we do not set the header
        }
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
        ctx.withHttpResponseTransformed { response =>
          cache.update(key, response)
          response
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
        ctx.withResponder { 
          _ match {
            case x@ Right(_) => ctx.responder(x) // first route succeeded
            case Left(rejections1) => other {
              ctx.withResponder {
                _ match {
                  case x@ Right(_) => ctx.responder(x) // second route succeeded
                  case Left(rejections2) => Left(rejections1 ++ rejections2)  
                }
              }
            }  
          }
        }
      }
    }
  }
  
  implicit def defaultDetachedActorFactory(route: Route): Actor = new DetachedRouteActor(route)
  
  implicit def defaultCacheKeyer(ctx: RequestContext): CacheKey = CacheOn(ctx.request.uri)
  
}