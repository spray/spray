package cc.spray.caching

import org.specs2.mutable._
import akka.actor.Actor
import java.util.concurrent.CountDownLatch
import akka.util.Duration
import util.Random
import akka.dispatch.Future
import org.specs2.matcher.Matcher

class LruCacheSpec extends Specification {

  "An LruCache" should {
    "be initially empty" in {
      LruCache().store.toString mustEqual "Map()"
    }
    "store uncached values" in {
      val cache = LruCache[String]()
      cache(1)("A").get mustEqual "A"
      cache.store.toString mustEqual "Map(1 -> A)"
    }
    "return stored values upon cache hit on existing values" in {
      val cache = LruCache[String]()
      cache(1)("A").get mustEqual "A"
      cache(1)("").get mustEqual "A"
      cache.store.toString mustEqual "Map(1 -> A)"
    }
    "return Futures on uncached values during evaluation and replace these with the value afterwards" in {
      val cache = LruCache[String]()
      val latch = new CountDownLatch(1)
      val future1 = cache(1) { completableFuture =>
        Actor.spawn {
          latch.await()
          completableFuture.completeWithResult("A")
        }
      }
      val future2 = cache(1)("")
      cache.store.toString mustEqual "Map(1 -> pending)"
      latch.countDown()
      future1.get mustEqual "A"
      future2.get mustEqual "A"
      cache.store.toString mustEqual "Map(1 -> A)"
    }
    "properly limit capacity" in {
      val cache = LruCache[String](maxEntries = 3)
      cache(1)("A").get mustEqual "A"
      cache(2)("B").get mustEqual "B"
      cache(3)("C").get mustEqual "C"
      cache.store.toString mustEqual "Map(1 -> A, 2 -> B, 3 -> C)"
      cache(4)("D")
      cache.store.toString mustEqual "Map(2 -> B, 3 -> C, 4 -> D)"
    }
    "honor the dropFraction param during resizing" in {
      val cache = LruCache[String](maxEntries = 3, dropFraction = 0.4)
      cache(1)("A").get mustEqual "A"
      cache(2)("B").get mustEqual "B"
      cache(3)("C").get mustEqual "C"
      cache.store.toString mustEqual "Map(1 -> A, 2 -> B, 3 -> C)"
      cache(4)("D")
      cache.store.toString mustEqual "Map(3 -> C, 4 -> D)"
    }
    "expire old entries" in {
      val cache = LruCache[String](ttl = Duration("20 ms"))
      cache(1)("A").get mustEqual "A"
      cache(2)("B").get mustEqual "B"
      Thread.sleep(10)
      cache(3)("C").get mustEqual "C"
      cache.store.toString mustEqual "Map(1 -> A, 2 -> B, 3 -> C)"
      Thread.sleep(10)
      cache.get(2) must beNone // triggers clean up, also of earlier entries
      cache.store.toString mustEqual "Map(3 -> C)"
    }
    "refresh an entries expiration time on cache hit" in {
      val cache = LruCache[String]()
      cache(1)("A").get mustEqual "A"
      cache(2)("B").get mustEqual "B"
      cache(3)("C").get mustEqual "C"
      cache(1)("").get mustEqual "A" // refresh
      cache.store.toString mustEqual "Map(2 -> B, 3 -> C, 1 -> A)"
    }
    "be thread-safe" in {
      val cache = LruCache[Int](maxEntries = 1000)
      // exercise the cache from 10 parallel "tracks" (threads)
      val views = Future.traverse(Seq.tabulate(10)(identity), Long.MaxValue) { track =>
        Future {
          val array = Array.fill(1000)(0) // our view of the cache
          val rand = new Random(track)
          (1 to 10000) foreach { i =>
            val ix = rand.nextInt(1000)            // for a random index into the cache
            val value = cache(ix) {                // get (and maybe set) the cache value
              Thread.sleep(0)
              rand.nextInt(1000000) + 1
            }.get
            if (array(ix) == 0) array(ix) = value  // update our view of the cache
            else if (array(ix) != value) failure("Cache view is inconsistent (track " + track + ", iteration " + i +
              ", index " + ix + ": expected " + array(ix) + " but is " + value)
          }
          array
        }
      }.get
      val beConsistent: Matcher[Seq[Int]] = (
        (ints: Seq[Int]) => ints.filter(_ != 0).reduceLeft((a, b) => if (a == b) a else 0) != 0,
        (_: Seq[Int]) => "consistency check"
      )
      views.transpose must beConsistent.forall
    }
  }
}
