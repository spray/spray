package cc.spray.cache

import akka.util.{Duration, ReadWriteGuard}

object LRUCache extends RegistryCacheBackend {
  type CacheType = LRUCache
  val defaultConfig = Map(
      "maxEntries"  -> "300", // triggers drop
      "dropFraction"-> "5",   // as inverse: 300/5 = 60
      "timeToLive"  -> "5 minutes", // default ttl
      "timeToHerd"  -> "5 seconds") // calms thundering herd
  protected def create(name: String, config: Map[String, String]) = {
    val c = defaultConfig ++ config
    new LRUCache(name,
        c("maxEntries").toInt,
        c("dropFraction").toInt,
        Duration(c("timeToLive")),
        Duration(c("timeToHerd")))
  }
}
/**
 *
 */
class LRUCache(val name: String,
               val maxEntries: Int,
               val dropFraction: Int,
               val timeToLive: Duration,
               val timeToHerd: Duration) extends ReadWriteGuard with Cache[Any] {
  import lru.{Store, Zero, Cowboy, StoreEntry => Entry}
  val store = new Store(maxEntries, dropFraction)
  def get(key: Any) = withReadGuard(store.find(key)) match {
    case e: Entry => if (e.expired) withWriteGuard {
      if (e.herds > Zero)         // replace stale value with cowboy
        store.replace(_.isClean(e))(key, e.value, e.herds, Cowboy)
      else store.evict(_.isClean(e))(key)
      None
    } else Some(e.value)
    case _ => None
  }
  def set(key: Any, value: Any, ttl: Option[Duration] = None) {
    if (value != null)
      withWriteGuard(store.store(key, value, ttl.getOrElse(timeToLive), timeToHerd))
  }
  def delete(key: Any) {
    withWriteGuard(store.remove(key))
  }
  def apply(key: Any, ttl: Option[Duration] = None)(expr: =>Any) = {
    var res: Any = null
    var tryAdd = false; var tryReplace = false
    var e = withReadGuard(store.find(key))
    if (e != null) {
      if (!e.expired) {           // return fresh value if found
        res = e.value
      } else if (e.herds > Zero) {// try to replace stale value with cowboy
        withWriteGuard {          // update the entry copy
          e = store.replace(_.isClean(e))(key, e.value, e.herds, Cowboy)
        }
        if (e != null) {
          if (Cowboy == e.herds)
            tryReplace = true     // try to replace the cowboy
          else res = e.value      // return if not cowboy
        } else tryAdd = true      // try to add if deleted before cowboy replace
      } else tryReplace = true    // try to replace stale value
    } else tryAdd = true          // try to add value if not found
    if (tryAdd || tryReplace) {
      val check = withReadGuard(store.find(key))
      if (check != null && Cowboy != check.herds && !check.expired) {
        res = check.value         // return updated value
      } else {
        res = expr                // dont lock evaluation
        withWriteGuard {
          if (tryAdd) store.add(key, res, ttl.getOrElse(timeToLive), timeToHerd)
          else store.replace(_.isClean(e))(key, res, ttl.getOrElse(timeToLive), timeToHerd)
        }
      }
    }
    res
  }
}
package object lru {
  import collection.mutable.{HashTable, HashEntry}
  val Zero = Duration(0, "s")
  val Cowboy = Duration(-1, "s")
  protected def expireTimeMillis(ttl: Duration): Long = {
    if (ttl == Zero || ttl == Duration.Inf) 0L
    else ttl.toMillis + System.currentTimeMillis
  }
  class StoreEntry protected(val key: Any,
                             var value: Any,
                             var expires: Long,
                             var herds: Duration) extends HashEntry[Any, StoreEntry]{
    var before: StoreEntry = null
    var after: StoreEntry = null
    def this(k: Any, v: Any, ttl: Duration, h: Duration) = this(k, v, expireTimeMillis(ttl), h)
    def copy = new StoreEntry(key, value, expires, herds)
    def change(v:Any, ttl: Duration, h: Duration) {
      value = v
      expires = expireTimeMillis(ttl)
      herds = h
    }
    def expired = expires > 0L && expires <= System.currentTimeMillis
    def isClean(c: StoreEntry) = { // same key implied
      c != null && expires == c.expires && herds == c.herds && value == c.value
    }
  }
  class Store(val maxEntries: Int, val dropFraction: Int) extends HashTable[Any, StoreEntry] {
    type Entry = StoreEntry
    _loadFactor = 1000
    override protected def initialSize = (maxEntries*1.1+1).toInt
    protected var head: Entry = null
    protected var tail: Entry = null
    def size = tableSize
    def traceKeys: String = {
      var res = ""
      var cur = head
      while (cur != null) {
        res += cur.key.toString
        cur = cur.after
      }
      res
    }
    def find(key: Any, quiet: Boolean=false): Entry = findEntry(key) match {
      case e: Entry => if (quiet || e.expired) e.copy else makeHead(e).copy
      case null => null
    }
    def remove(key: Any) {
      val e = removeEntry(key)
      if (e != null) unlink(e)
    }
    def evict(condition: Entry => Boolean)(key: Any) {
      val e = findEntry(key)
      if (e != null && condition(e)) {
        removeEntry(key)
        unlink(e)
      }
    }
    def store(key: Any, value: Any, ttl: Duration, herd: Duration=Zero) {
      findEntry(key) match {
        case e: Entry => {
          e.change(value, ttl, herd)
          makeHead(e)
        }
        case _ => {
          val e = new Entry(key, value, ttl, herd)
          if (tableSize >= maxEntries)
            dropTail(dropFraction)
          addEntry(e)
          addHead(e)
        }
      }
    }
    def add(key: Any, value: Any, ttl: Duration, herd: Duration=Zero) {
      findEntry(key) match {
        case e: Entry => makeHead(e)
        case null => {
          val e = new Entry(key, value, ttl, herd)
          if (tableSize >= maxEntries)
            dropTail(dropFraction)
          addEntry(e)
          addHead(e)
        }
      }
    }
    def replace(condition: Entry => Boolean)
               (key: Any, value: Any, ttl: Duration, herd: Duration=Zero): Entry = {
      findEntry(key) match {
        case e: Entry => if (condition(e)) {
          e.change(value, ttl, herd)
          makeHead(e).copy
        } else e.copy
        case _ => null
      }
    }
    def dropTail(frac: Int) {
      if (frac > 1 && maxEntries > 1) {
        val toSize = maxEntries - math.max(maxEntries/frac, 1)
        val cur = tail
        while (tableSize > toSize && cur != head) {
          removeEntry(cur.key)
          tail = cur.before
          tail.after = null
          cur.before = null
        }
      } else clear()
    }
    def clear() {
      clearTable()
      head = null
      tail = null
    }
    protected def makeHead(e: Entry) = {
      if (e.before != null) { unlink(e); addHead(e) }
      e
    }
    protected def addHead(e: Entry) {
      if (tail == null) tail = e
      else { head.before = e; e.after = head }
      head = e
    }
    protected def unlink(e: Entry) {
      if (e == head) head = e.after
      else e.before.after = e.after
      if (e == tail) tail = e.before
      else e.after.before = e.before
      e.before = null; e.after = null
    }
  }
}
