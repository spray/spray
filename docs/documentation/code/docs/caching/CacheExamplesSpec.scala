package docs.caching

import org.specs2.mutable.Specification

//#imports1
import akka.dispatch.Future
import akka.actor.ActorSystem
import cc.spray.caching.{LruCache, Cache}
import cc.spray.util._

//#imports1

class CacheExamplesSpec extends Specification {
  implicit val system = ActorSystem()

  "exercise a simple cache" in {
    //#simple-caching-example
// if we have an "expensive" operation
def expensiveOp(): Double = new util.Random().nextDouble()

// and a Cache for its result type
val cache: Cache[Double] = LruCache()

// we can wrap the operation with caching support
// (providing a caching key)
def cachedOp[T](key: T): Future[Double] = cache(key) { expensiveOp() }

// and profit
cachedOp("foo").await mustEqual cachedOp("foo").await
cachedOp("bar").await mustNotEqual cachedOp("foo").await
    //#simple-caching-example
  }

  step(system.shutdown())
}
