package cc.spray
package builders

import util.matching.Regex

private[spray] trait PathBuilders {
  
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
        f(captures)(
          if (remainingPath == "") {
            // if we have successfully matched the complete URI we need to  
            // add a PathMatchedRejection if the request is rejected by some inner filter
            ctx.copy(unmatchedPath = "", responder = { rr =>
              ctx.responder {
                rr match {
                  case x@ Right(_) => x // request succeeded, no further action required
                  case Left(rejections) => Left(rejections + PathMatchedRejection) // rejected, add marker  
                }
              }
            })
          } else {
            ctx.copy(unmatchedPath = remainingPath)
          }
        )
      }
      case None => ctx.reject()
    }
  } 
  
  // implicits
  
  implicit def string2Matcher(s: String): PathMatcher0 = new StringMatcher(s)
  
  implicit def regex2Matcher(regex: Regex): PathMatcher1 = {
    regex.groupCount match {
      case 0 => new SimpleRegexMatcher(regex)
      case 1 => new GroupRegexMatcher(regex)
      case 2 => throw new IllegalArgumentException("Path regex '" + regex.pattern.pattern +
              "' must not contain more than one capturing group")
    }
  }
  
}