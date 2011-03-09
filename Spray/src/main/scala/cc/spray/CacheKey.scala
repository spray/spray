package cc.spray

sealed trait CacheKey

case class CacheOn(key: Any) extends CacheKey

case object DontCache extends CacheKey