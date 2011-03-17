package cc.spray
package builders

import http._
import HttpMethods._
import util.matching.Regex

private[spray] trait SimpleFilterBuilders {
  this: FilterBuilders =>
  
  def delete  = method(DELETE)
  def get     = method(GET)
  def head    = method(HEAD)
  def options = method(OPTIONS)
  def post    = method(POST)
  def put     = method(PUT)
  def trace   = method(TRACE)
  
  def method(m: HttpMethod) = filter { ctx =>
    if (ctx.request.method == m) None else Some(MethodRejection(m) :: Nil) 
  }
  
  def host(hostName: String): FilterRoute0 = host(_ == hostName)
  
  def host(predicate: String => Boolean): FilterRoute0 = filter { ctx =>
    if (predicate(ctx.request.host)) None else Some(Nil)
  }
  
  def host(regex: Regex): FilterRoute[String] = filter1 { ctx =>
    def run(regexMatch: String => Option[String]) = {
      regexMatch(ctx.request.host) match {
        case Some(matched) => Right(matched)
        case None => Left(Nil)
      }
    }
    regex.groupCount match {
      case 0 => run(regex.findPrefixOf(_))
      case 1 => run(regex.findPrefixMatchOf(_).map(_.group(1)))
      case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
              "' must not contain more than one capturing group")
    }
  }
  
}
