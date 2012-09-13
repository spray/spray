package docs

import org.specs2.mutable.Specification

import akka.dispatch.Future                 // example-1
import akka.actor.ActorSystem               // example-1
import cc.spray.caching.{LruCache, Cache}   // example-1
import cc.spray.util._                      // example-1


class CachingExamplesSpec extends Specification {
                                      // example-1
  implicit val system = ActorSystem() // example-1

  "example-1" in {

    // if we have an "expensive" operation
    def expensiveOp(): Double = new util.Random().nextDouble()

    // and a Cache for its result type
    val cache: Cache[Double] = LruCache()

    // we can wrap the operation with caching support
    // (providing a caching key)
    def cachedOp[T](key: T): Future[Double] = cache(key) {
      expensiveOp()
    }

    // and profit
    cachedOp("foo").await === cachedOp("foo").await
    cachedOp("bar").await !== cachedOp("foo").await
  }

  step(system.shutdown())
}
