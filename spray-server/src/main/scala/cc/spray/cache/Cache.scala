package cc.spray.cache
import akka.util.Duration
import collection.JavaConversions.JConcurrentMapWrapper
import java.util.concurrent.ConcurrentHashMap

/**
 * A single repository of cached key value pairs.
 */
trait Cache[V] {
  def name: String
  def timeToLive: Duration

  def apply(key: Any, ttl: Option[Duration] = None)(expr: => V): V

  def get(key: Any): Option[V]
  def set(key: Any, value: V, ttl: Option[Duration] = None)
  def delete(key: Any)

  // add/replace ? could be emulated if not provied as atomic operation
  //def clear() we cannot clear a region(by prefix) with memcached
}

/**
 * A CacheBackend manages a number of Caches.
 */
trait CacheBackend {
  type CacheType <: Cache[Any]

  /* Returns the cache with the given name.
   * Creates a new cache if not already there.
   */
  def apply(name: String, options: Map[String, String] = Map.empty): CacheType
}

trait RegistryCacheBackend extends CacheBackend {
  protected val caches = JConcurrentMapWrapper(new ConcurrentHashMap[String, CacheType])

  def apply(name: String, options: Map[String, String] = Map.empty) = {
    caches.getOrElseUpdate(name, create(name, options))
  }

  protected def create(name: String, options: Map[String, String]): CacheType
}
