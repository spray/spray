package cc.spray.utils

trait ObjectRegistry[K, V] {
  protected val registry = collection.mutable.Map.empty[K, V]
  
  def register(obj: V, keys: Seq[K]) {
    keys.foreach(register(obj, _))
  }
  
  def register(obj: V, key: K) {
    registry.update(key, obj)
  }
  
  def getForKey(key: K): Option[V] = {
    registry.get(key)
  }
}