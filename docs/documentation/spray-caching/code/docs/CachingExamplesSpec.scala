package docs

import org.specs2.mutable.Specification

//# example-1
import akka.dispatch.Future
import akka.actor.ActorSystem
import spray.caching.{LruCache, Cache}
import spray.util._

//#

class CachingExamplesSpec extends Specification {
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
