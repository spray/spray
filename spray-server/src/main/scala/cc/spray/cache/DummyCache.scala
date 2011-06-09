package cc.spray.cache
import akka.util.Duration

object DummyCache extends CacheBackend {
  type CacheType = DummyCache
  def apply(name: String, config: Map[String, String]=Map.empty) = {
    new DummyCache(name)
  }
}

class DummyCache(val name: String) extends Cache[Any] {
  val timeToLive = Duration.MinusInf
  def get(key: Any): Option[Any] = None
  def set(key: Any, value: Any, ttl: Duration=timeToLive) {}
  def delete(key: Any) {}
  def apply(key: Any, expr: =>Any, ttl: Duration=timeToLive) = expr

  override def hashCode = name.hashCode
  override def equals(other: Any) = other match {
    case o: DummyCache => name == o.name
    case _ => false
  }
}
