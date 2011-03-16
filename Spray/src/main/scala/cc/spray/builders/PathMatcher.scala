package cc.spray.builders

import util.matching.Regex

/**
 * a PathMatcher tries to match a prefix of a given string and returns
 * - None if not matched
 * - Some(remainingPath, captures) if matched
 */
sealed trait PathMatcher extends (String => Option[ (String, List[String]) ])

sealed trait PathMatcher0 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def / (sub: PathMatcher3) = this ~ Slash ~ sub
  def / (sub: PathMatcher4) = this ~ Slash ~ sub
  def / (sub: PathMatcher5) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher0
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher1
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher2
  def ~ (sub: PathMatcher3) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher4) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher5) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher1 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def / (sub: PathMatcher3) = this ~ Slash ~ sub
  def / (sub: PathMatcher4) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher1
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher2
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher3) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher4) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher2 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def / (sub: PathMatcher3) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher2
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher3) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher3 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def / (sub: PathMatcher2) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher3
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher2) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher4 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def / (sub: PathMatcher1) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher4
  def ~ (sub: PathMatcher1) = new Combi(this, sub) with PathMatcher5
}

sealed trait PathMatcher5 extends PathMatcher {
  def / (sub: PathMatcher0) = this ~ Slash ~ sub
  def ~ (sub: PathMatcher0) = new Combi(this, sub) with PathMatcher5
}

private[spray] class Combi(a: PathMatcher, b: PathMatcher) {
  def apply(path: String) = a(path).flatMap {
    case (restA, capturesA) => b(restA).map {
      case (restB, capturesB) => (restB, capturesA ::: capturesB) 
    }
  }
}

object Slash extends PathMatcher0 {
  def apply(path: String) = {
    if (path.length > 0 && path.charAt(0) == '/') Some((path.substring(1), Nil)) else None
  }
}

object Remaining extends PathMatcher1 {
  def apply(path: String) = Some(("", path :: Nil))
}

private[builders] class StringMatcher(prefix: String) extends PathMatcher0 {
  def apply(path: String) = {
    if (path.startsWith(prefix)) Some((path.substring(prefix.length), Nil)) else None
  } 
}

private[builders] class SimpleRegexMatcher(regex: Regex) extends PathMatcher1 {
  def apply(path: String) = {
    regex.findPrefixOf(path).map(matched => (path.substring(matched.length), matched :: Nil))
  }
}

private[builders] class GroupRegexMatcher(regex: Regex) extends PathMatcher1 {
  def apply(path: String) = {
    regex.findPrefixMatchOf(path).map { m =>
      val matchLength = m.end - m.start
      (path.substring(matchLength), m.group(1) :: Nil)
    }
  }
}