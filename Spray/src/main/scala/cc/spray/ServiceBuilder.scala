package cc.spray

import http._
import HttpHeaders._
import HttpMethods._
import java.util.regex.Pattern
import util.matching.Regex
import akka.actor.Actor
import org.parboiled.common.FileUtils
import java.io.File

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
        assert(f.isDefinedAt(captures)) // static typing should ensure that we match the right number of captures
        val route = f(captures)
        route(ctx.copy(unmatchedPath = remainingPath))
      }
      case _ => ctx.respondUnhandled
    }
  } 
  
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
  
  def getFromFile(filename: String): Route = getFromFile(new File(filename))
  
  def getFromFile(file: File)(implicit detachedActorFactory: Route => Actor,
                              mimeType4FileResolver: File => MimeType): Route = {
    detached {
      produces(mimeType4FileResolver(file)) {
        get { ctx =>
          val content = FileUtils.readAllBytes(file)
          if (content != null) ctx.respond(content)
          else ctx.fail(HttpStatusCodes.InternalServerError, "File '" + file + "' not found")
        }
      }
    }
  }
  
  def getFromResource(resourceName: String)(implicit detachedActorFactory: Route => Actor,
                                            mimeType4FileResolver: File => MimeType): Route = {
    detached {
      produces(mimeType4FileResolver(new File(resourceName))) {
        get { ctx =>
          val content = FileUtils.readAllBytesFromResource(resourceName)
          if (content != null) ctx.respond(content)
          else ctx.fail(HttpStatusCodes.InternalServerError, "Resource '" + resourceName + "' not found")
        }
      }
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
  
  implicit def string2Matcher(s: String): PathMatcher0 = new StringMatcher(s)
  
  implicit def regex2Matcher(regex: Regex): PathMatcher1 = getGroupCount(regex) match {
    case 0 => new SimpleRegexMatcher(regex)
    case 1 => new GroupRegexMatcher(regex)
    case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
            "' must not contain more than one capturing group")
  }
  
  implicit def defaultDetachedActorFactory(route: Route): Actor = new DetachedRouteActor(route)
  
  implicit def defaultMimeType4FileResolver(file: File): MimeType = {
    MimeTypes.forExtension(file.extension).getOrElse(MimeTypes.`application/octet-stream`)
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