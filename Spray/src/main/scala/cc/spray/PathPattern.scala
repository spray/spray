package cc.spray

trait PathPattern0 {
  def / (sub: PathPattern0): PathPattern0
  def / (sub: PathPattern1): PathPattern1
  def / (sub: PathPattern2): PathPattern2
}
trait PathPattern1 {
  def / (sub: PathPattern0): PathPattern1
  def / (sub: PathPattern1): PathPattern2
  def / (sub: PathPattern2): PathPattern3
}
trait PathPattern2 {
  def / (sub: PathPattern0): PathPattern2
  def / (sub: PathPattern1): PathPattern3
}
trait PathPattern3 {
}

object PathPattern

class StringPattern {
  
}

class RegexPattern {
  
}