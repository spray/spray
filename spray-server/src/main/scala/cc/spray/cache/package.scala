package cc.spray.cache
import akka.util.Duration
trait Cache[V] {
  def timeToLive: Duration
  def name: String
  // i decided against apply for get and update for set 
  def get(key: Any): Option[V]
  def set(key: Any, value: V, ttl: Duration=timeToLive)
  // add/replace ? could be emulated if not provied as atomic operation
  def delete(key: Any)
  //def clear() we cannot clear a region(by prefix) with memcached
  // get/setMany should be added
  def apply(key: Any, value: =>V, ttl: Duration=timeToLive): V
  //def do[T](key: Any, exists: V=>T, otherwise: =>T):T
}
trait CacheBackend {
  type CacheType <: Cache[Any]
  /* Returns the cache with the given name.
   * Creates a new cache if not already there.
   */
  def apply(name: String, options: Map[String, String]=Map.empty): CacheType
}
trait CacheRegister { self:CacheBackend =>
  import collection.mutable.HashMap
  protected val lock:AnyRef = new Object()
  protected val caches = HashMap.empty[String, CacheType]
  def apply(name: String, options: Map[String, String]=Map.empty) = 
    caches.getOrElse(name, lock.synchronized {
      caches.getOrElseUpdate(name, create(name, options))
    })
  protected def create(name: String, options: Map[String, String]): CacheType
}
