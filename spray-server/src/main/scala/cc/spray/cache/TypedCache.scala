package cc.spray.cache
import reflect.Manifest
import akka.util.Duration

/**
 * This class can wrap a Cache[Any] instance to be used as a Cache[V].
 * Key namespace problems can result in wrong value types.
 * In that case the wrapper will fail silently.
 */
class TypedCache[V] protected(protected val clas: Class[_], val wrapped:Cache[Any])
        extends Cache[V] {
  var empty: V = _
  def timeToLive = wrapped.timeToLive
  def this(w: Cache[Any], c: Class[V]) = this(c, w)
  def this(w: Cache[Any])(implicit m: Manifest[V]) = this(m.erasure, w)
  
  def name = wrapped.name

  def set(key: Any, value: V, ttl: Duration=timeToLive) {
    wrapped.set(key, value, ttl)
  }

  def delete(key: Any) = wrapped.delete(key)

  /**
   * Returns Some(value) associated with the give key or None.
   * If the value cannot be assigned to V None is returned.
   */
  def get(key: Any):Option[V] = wrapped.get(key) match {
    // TODO: log something when the type check fails so the admin may fix his cache setup
    case Some(v) if clas.isInstance(v) => Some(v.asInstanceOf[V])
    case _ => None
  }

  /**
   * Returns the value associated with the given key
   * otherwise evaluates, sets and returns the given expression.
   * If the value cannot be assigned to V the expression will be
   * evaluated without updating the cache.
   */
  def apply(key: Any, expr: =>V, ttl: Duration=timeToLive): V = {
    wrapped(key, expr) match {
      // TODO: log something when the type check fails so the admin may fix his cache setup
      case null => empty // cannot be from cache. dont evaluate expr again
      case v if clas.isInstance(v) => v.asInstanceOf[V]
      case _ => expr
    }
  }
}
