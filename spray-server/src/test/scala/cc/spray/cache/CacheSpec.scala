package cc.spray.cache

import org.specs.Specification

trait CacheSpec {
  self: Specification =>

  def backendBehaviour(backend: CacheBackend) {
    "named Cache[Any] instances" in {
      backend("foo").name mustEqual "foo"
    }
    "equal caches for equal names" in {
      backend("bar") mustEqual backend("bar")
    }
  }

  def basicBehaviour(cacheBuilder: String => Cache[Any]) = {
    val cache = cacheBuilder("basic")
    "the cache name" in {
      cache.name mustEqual "basic"
    }
    "None for an unknown key" in {
      cache.get("k") mustBe None
    }
    "the evaluated expr for an unknown key" in {
      cache("k")(42) mustBe 42
      cache.delete("k")
    }
  }

  def storeBehaviour(cacheBuilder: String => Cache[Any]) = {
    val cache = cacheBuilder("store")
    "the given value on set" in {
      cache.set("k", 123)
      cache.get("k") mustEqual Some(123)
      cache.delete("k")
      cache.get("k") mustBe None
    }
    "the evaluated expr iff key unknown on getOrSet" in {
      cache("k")(321) mustEqual 321
      cache.get("k") mustEqual Some(321)
      cache("k"){fail();123} mustEqual 321
      cache.delete("k")
      cache.get("k") mustBe None
    }
  }
}
