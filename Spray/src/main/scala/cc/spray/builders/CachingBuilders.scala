package cc.spray
package builders

import http._
import collection.mutable.WeakHashMap

private[spray] trait CachingBuilders {
  
  def cached(route: Route)(implicit keyer: RequestContext => CacheKey): Route = new Route {
    private val cache = WeakHashMap.empty[Any, HttpResponse]
    
    def apply(ctx: RequestContext) {
      val key = if (ctx.request.method == HttpMethods.GET) keyer(ctx) else DontCache
      if (key eq DontCache) {
        route(ctx)
      } else {
        cache.get(key) match {
          case Some(response) => ctx.responder(Right(response))
          case None => route {
            ctx.withHttpResponseTransformed { response =>
              cache.update(key, response)
              response
            }
          }
        }
      }
    }
  }
  
  // implicits  
  
  implicit def defaultCacheKeyer(ctx: RequestContext): CacheKey = CacheOn(ctx.request.uri)
  
}

sealed trait CacheKey
case class CacheOn(key: Any) extends CacheKey
case object DontCache extends CacheKey