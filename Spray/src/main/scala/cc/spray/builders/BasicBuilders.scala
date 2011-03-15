package cc.spray
package builders

import http._
import HttpMethods._
import akka.actor.Actor
import collection.mutable.WeakHashMap
import util.matching.Regex

private[spray] trait BasicBuilders {
  
  // TODO:
  // - parameter
  // - optionalParameter
  
  def methods(m: HttpMethod*) = filter(m.map(MethodRejection(_))) { ctx => m.exists(_ == ctx.request.method) } _
  
  def delete  = filter(Seq(MethodRejection(DELETE)))  { _.request.method == DELETE } _
  def get     = filter(Seq(MethodRejection(GET)))     { _.request.method == GET } _
  def head    = filter(Seq(MethodRejection(HEAD)))    { _.request.method == HEAD } _
  def options = filter(Seq(MethodRejection(OPTIONS))) { _.request.method == OPTIONS } _
  def post    = filter(Seq(MethodRejection(POST)))    { _.request.method == POST } _
  def put     = filter(Seq(MethodRejection(PUT)))     { _.request.method == PUT } _
  def trace   = filter(Seq(MethodRejection(TRACE)))   { _.request.method == TRACE } _
  
  def host(hostName: String): Route => Route = host(_ == hostName)
  
  def host(predicate: String => Boolean): Route => Route = filter(Nil) { ctx => predicate(ctx.request.host) } _
  
  def host(regex: Regex)(routing: String => Route): Route = {
    def complete(regexMatch: String => Option[String]): Route = { ctx =>
      regexMatch(ctx.request.host) match {
        case Some(matched) => routing(matched)(ctx)
        case None => ctx.reject()
      }
    }
    regex.groupCount match {
      case 0 => complete(regex.findPrefixOf(_))
      case 1 => complete(regex.findPrefixMatchOf(_).map(_.group(1)))
      case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
              "' must not contain more than one capturing group")
    }
  }

  def filter(rejections: Seq[Rejection])(p: RequestContext => Boolean)(route: Route): Route = { ctx =>
    if (p(ctx)) route(ctx) else ctx.reject(rejections: _*)
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
      cache.get(key) match {
        case Some(response) => ctx.respond(response)
        case None => route {
          ctx.withHttpResponseTransformed { response =>
            cache.update(key, response)
            response
          }
        }
      }
    }
  }
  
  // uncachable
  def hardFail(failure: HttpFailure, reason: String = ""): Nothing = throw new HttpException(failure, reason)
  
  
  // implicits
  
  implicit def route2RouteConcatenation(route: Route): { def ~ (other: Route): Route } = new {
    def ~ (other: Route): Route = { ctx =>
      route {
        ctx.withResponder { 
          _ match {
            case x@ Right(response) => ctx.respond(x) // first route succeeded
            case Left(rejections1) => other {
              ctx.withResponder {
                _ match {
                  case x@ Right(_) => ctx.respond(x) // second route succeeded
                  case Left(rejections2) => ctx.respond(Left(rejections1 ++ rejections2))  
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