package cc.spray.cache

import org.specs.Specification

class TypedCacheSpec extends Specification with CacheSpec {
  val c = LRUCache("name")
  "TypedCache" can {
    "be constructed with" >> {
      "Class argument" in {
        val tc = new TypedCache(c, classOf[String])
        tc must haveClass[TypedCache[String]]
      }
      "implicit Manifest" in {
        val tc = new TypedCache[String](c)
        tc must haveClass[TypedCache[String]]
      }
    }
  }
  //def fix[V] = ...//wont compile use:
  def fix[V: reflect.Manifest](n: String="name") = {
    new TypedCache[V](LRUCache(n))
  }
  "TypedCache" should {
    "provide" >> {
      basicBehaviour(fix[Any])
      storeBehaviour(fix[Any])
    }
    "have type safe default behaviour" in {
      val tc1 = fix[Double]()
      //tc1("foo", "bar") //wont compile
      tc1("foo", 42) mustEqual 42.0 // type conversion
      val tc2 = fix[String]()
      tc2.get("foo") mustBe None
      tc2("foo", "bar") mustEqual "bar"
      c.get("foo") mustEqual Some(42.0)
    }
    "not accept null" in {
      var i = 0
      val tc = fix[String]()
      tc.set("a", null)
      tc.get("a") mustBe None
      tc("a",{i+=1; if(i != 1) fail("executed two times"); null}) mustBe null
      tc.get("a") mustBe None
    }
  }
}
