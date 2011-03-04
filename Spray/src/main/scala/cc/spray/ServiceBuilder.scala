package cc.spray

import http._
import HttpHeaders._
import HttpMethods._
import java.util.regex.Pattern
import util.matching.Regex

trait ServiceBuilder {
  
  def path(pattern: PathMatcher0)(route: Route) = pathFilter(Slash ~ pattern) {
    case Nil => route
  }
  
  def path(pattern: PathMatcher1)(routing: String => Route) = pathFilter(Slash ~ pattern) {
    case a :: Nil => routing(a)
  }
  
  def path(pattern: PathMatcher2)(routing: (String, String) => Route) = pathFilter(Slash ~ pattern) {
    case a :: b :: Nil => routing(a, b)
  }
  
  def path(pattern: PathMatcher3)(routing: (String, String, String) => Route) = pathFilter(Slash ~ pattern) {
    case a :: b :: c :: Nil => routing(a, b, c)
  }
  
  def path(pattern: PathMatcher4)(routing: (String, String, String, String) => Route) = pathFilter(Slash ~ pattern) {
    case a :: b :: c :: d :: Nil => routing(a, b, c, d)
  }
  
  def path(pattern: PathMatcher5)(routing: (String, String, String, String, String) => Route) = pathFilter(Slash ~ pattern) {
    case a :: b :: c :: d :: e :: Nil => routing(a, b, c, d, e)
  }
  
  private def pathFilter(pattern: PathMatcher)(f: PartialFunction[List[String], Route]): Route = { ctx =>
    pattern(ctx.unmatchedPath) match {
      case Some((remainingPath, captures)) => {
        assert(f.isDefinedAt(captures)) // the static typing should ensure that we match the right number of captures
        val route = f(captures)
        route((ctx.copy(unmatchedPath = remainingPath)))
      }
      case _ => false
    }
  } 
  
  def accepts(mimeTypes: MimeType*) = filter { ctx =>
    (ctx.request.extractFromHeader { case `Content-Type`(mimeType) => mimeType }) match {
      case Some(contentType) => mimeTypes.exists(_.matches(contentType))
      case _ => false
    }
  } _

  def produces(mimeType: MimeType)(route: Route): Route = { ctx =>
    route(ctx.withResponseHeader(`Content-Type`(mimeType)))
  }
  
  def methods(m: HttpMethod*) = filter(ctx => m.exists(_ == ctx.request.method)) _
  
  def delete  = filter(_.request.method == DELETE) _
  def get     = filter(_.request.method == GET) _
  def head    = filter(_.request.method == HEAD) _
  def options = filter(_.request.method == OPTIONS) _
  def post    = filter(_.request.method == POST) _
  def put     = filter(_.request.method == PUT) _
  def trace   = filter(_.request.method == TRACE) _

  def filter(p: Context => Boolean)(route: Route): Route = { ctx => p(ctx) && route(ctx) }
  
  
  // implicits
  
  
  implicit def route2RouteConcatenation(route: Route): { def ~ (other: Route): Route } = new {
    def ~ (other: Route): Route = { ctx => route(ctx) || other(ctx) }
  }
  
  implicit def string2Matcher(s: String): PathMatcher0 = new StringMatcher(s)
  
  implicit def regex2Matcher(regex: Regex): PathMatcher1 = getGroupCount(regex) match {
    case 0 => new SimpleRegexMatcher(regex)
    case 1 => new GroupRegexMatcher(regex)
    case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
            "' must not contain more than one capturing group")
  }
  
  
  // helpers
  
  
  private def getGroupCount(regex: Regex) = {
    try {
      val field = classOf[Pattern].getDeclaredField("capturingGroupCount")
      field.setAccessible(true)
      field.getInt(regex.pattern) - 1
    } catch {
      case t: Throwable =>
        throw new RuntimeException("Could not determine group count of path regex: " + regex.pattern.pattern, t)
    }
  }
  
}

object ServiceBuilder extends ServiceBuilder