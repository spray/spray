package cc.spray.cache

import org.specs.Specification
import akka.util.duration._

object TestStore extends lru.Store(2,1)
class LRUCacheSpec extends Specification with CacheSpec {
  "LRUCache" should {
    "provide" >> {
      def fix(n: String) = new LRUCache(n, 50, 50, 50.seconds, 0.seconds)
      backendBehaviour(LRUCache)
      basicBehaviour(fix)
      storeBehaviour(fix)
    }
    "limit the capacity" in {
      val c = new LRUCache("name", 2, 3, 50.seconds, 0.seconds)
      c("0",0); c("1",1); c("2",2)
      c.get("0") mustEqual None
      c.get("2") mustEqual Some(2)
      c.get("1") mustEqual Some(1)
      c("3",3)
      c.get("2") mustEqual None
    }
    "expire old entries" in {
      val c = new LRUCache("name", 10, 10, 1.second, 0.seconds)
      c.set("50", "millis", 50.millis)
      c.set("1", "second") // default 2.seconds
      c.get("50") mustEqual Some("millis")
      Thread.sleep(50)
      c.get("50") mustBe None
      c.get("1") mustEqual Some("second")
    }
    "calm thundering herd" in {
      val c = new LRUCache("name", 10, 10, 50.millis, 50.millis)
      c.set("foo", "bar")
      c.set("spam", "egg")
      Thread.sleep(20)
      c.get("foo") mustEqual Some("bar") // fresh
      Thread.sleep(30)
      c.get("foo") mustBe None           // stale
      // we are now herding! foo is a cowboy 
      // for 1 second and must be replaced
      c("foo","egg") mustEqual "bar"     // cowboy
      c.get("foo") mustEqual Some("bar") // cowboy
      // get or set replaces cowboys automatically
      c("spam", "bar") mustEqual "bar"   // fresh
      // if a cowboy is not replaced it will expire
      Thread.sleep(50)
      c.get("foo") mustBe None // really not herding?
      c.get("foo") mustBe None
    }
    "not lock evaluation in apply" in {
      import scala.actors.Futures
      val c = new LRUCache("name", 10, 10, 1.second, 0.seconds)
      val future = Futures.future {
        c(0,"some")
        c(1, { Thread.sleep(30); "done"})
      }
      Thread.sleep(10)
      // 0 was set
      c.get(0) mustEqual Some("some")
      // 1 evaluation is running but we can read
      c.get(1) mustBe None
      // we would refresh the value and we can. this is
      // not atomic but a compromise to avoid locking.
      // cowboys cant help if we have no initial value.
      c(1, "thundering") mustBe "thundering"
      // tip: one solution is to warm up the cache
      //      before the herd arrives and set timeToHerd
      //      greater then the max time needed
      // we always get the local value once evaluation started
      future() mustBe "done"
      // but the second call updated the cache already
      c.get(1) mustEqual Some("thundering")
    }
  }
  "lru.Store" should {
    import lru._
    val s = TestStore
    "return null if entry is not found" in {
      s.find(0) mustBe null
    }
    "return entry if found" in {
      s.store(0, "he", Zero)
      val e = s.find(0)
      e.value mustEqual "he"
      e.expires mustEqual 0
      e.herds mustEqual 0.seconds
    }
    "return a copy of the entry" in {
      val e = s.find(0)
      val e2 = s.find(0)
      e must_!= e2
      e.isClean(e2) mustBe true
      e.value = "muh"
      e.isClean(s.find(0)) mustBe false
    }
    "set entry expires time when stored" in {
      val e = s.find(0)
      e.expires mustEqual 0
      s.store(0, "he", 36.seconds)
      val now: Long = System.currentTimeMillis
      s.find(0).expires must be closeTo(now + 36000L, 1000L)
    }
    "update entries with equal keys" in {
      val e = s.find(0)
      s.store(0, "hi", Zero)
      val n = s.find(0)
      n.isClean(e) mustBe false
    }
    "promote entry when stored or found" in {
      s.store(1, "ho", Zero)
      s.traceKeys mustEqual "10"
      s.find(0).value mustEqual "hi"
      s.traceKeys mustEqual "01"
    }
    "not promote when quiet is true" in {
      s.find(1, true).value mustEqual "ho"
      s.traceKeys mustEqual "01"
    }
    "cleanly unlink entries when removed" in {
      val e = s.find(0)
      s.remove(0)
      s.traceKeys mustEqual "1"
      s.size mustEqual 1
      e.before must beNull
      e.after must beNull
    }
    "trigger dropTail when maxEntries are reached" in {
      s.store(2, "hu", Zero)
      s.traceKeys mustEqual "21"
      s.store(3, "ha", Zero) // triggers drop
      s.traceKeys mustEqual "3"
    }
  }
}
