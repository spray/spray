package cc.spray.cache
import akka.util.Duration

object DummyCache extends CacheBackend {
  type CacheType = DummyCache

  def apply(name: String, config: Map[String, String] = Map.empty) = new DummyCache(name)
}

case class DummyCache(name: String) extends Cache[Any] {
  val timeToLive = Duration.MinusInf

  def apply(key: Any, ttl: Option[Duration] = None)(expr: => Any) = expr

  def get(key: Any): Option[Any] = None
  def set(key: Any, value: Any, ttl: Option[Duration] = None) {}
  def delete(key: Any) {}
}
