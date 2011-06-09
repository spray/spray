package cc.spray.cache

import org.specs.Specification

class DummyCacheSpec extends Specification with CacheSpec {
  "DummyCache" should {
    "provide" >> {
      backendBehaviour(DummyCache)
      basicBehaviour(DummyCache(_))
    }
  }
}
