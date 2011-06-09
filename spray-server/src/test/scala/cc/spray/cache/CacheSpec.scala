package cc.spray.cache

import org.specs.Specification

trait CacheSpec { self: Specification =>
  def backendBehaviour(be: CacheBackend) {
    "named Cache[Any] instances" in {
      val c: Cache[Any] = be("foo")
      c.name mustEqual "foo"
    }
    "equal caches for equal names" in {
      be("bar") mustEqual be("bar")
    }
  }
  def basicBehaviour(cache: String => Cache[Any]) = {
    val c = cache("basic")
    "the cache name" in {
      c.name mustEqual "basic"
    }
    "None for an unknown key" in {
      c.get("k") mustBe None
    }
    "the evaluated expr for an unknown key" in {
      c("k", 42) mustBe 42
      c.delete("k")
    }
  }
  def storeBehaviour(cache: String => Cache[Any]) = {
    val c = cache("store")
    "the given value on set" in {
      c.set("k", 123)
      c.get("k") mustEqual Some(123)
      c.delete("k")
      c.get("k") mustBe None
    }
    "the evaluated expr iff key unknown on getOrSet" in {
      c("k", 321) mustEqual 321
      c.get("k") mustEqual Some(321)
      c("k", {fail();123}) mustEqual 321
      c.delete("k")
      c.get("k") mustBe None
    }
  }
}
