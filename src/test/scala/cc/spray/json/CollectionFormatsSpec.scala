package cc.spray.json

import org.specs.Specification
import java.util.Arrays

class CollectionFormatsSpec extends Specification with CollectionFormats with BasicFormats {

  "The listFormat" should {
    val list = List(1, 2, 3)
    val json = JsArray(JsNumber(1), JsNumber(2), JsNumber(3))
    "convert a List[Int] to a JsArray of JsNumbers" in {
      list.toJson mustEqual json
    }
    "convert a JsArray of JsNumbers to a List[Int]" in {
      json.fromJson[List[Int]] mustEqual list 
    }
  }
  
  "The arrayFormat" should {
    val array = Array(1, 2, 3)
    val json = JsArray(JsNumber(1), JsNumber(2), JsNumber(3))
    "convert an Array[Int] to a JsArray of JsNumbers" in {
      array.toJson mustEqual json
    }
    "convert a JsArray of JsNumbers to an Array[Int]" in {
      Arrays.equals(json.fromJson[Array[Int]], array) must beTrue 
    }
  }
  
  "The mapFormat" should {
    val map = Map("a" -> 1, "b" -> 2, "c" -> 3)
    val json = JsObject(JsField("a", 1), JsField("b", 2), JsField("c", 3))
    "convert a Map[String, Long] to a JsObject" in {
      map.toJson mustEqual json
    }
    "be able to convert a JsObject to a Map[String, Long]" in {
      json.fromJson[Map[String, Long]] mustEqual map 
    }
    "throw an Exception when trying to serialize a map whose key are not serialized to JsStrings" in {
      Map(1 -> "a").toJson must throwA(new SerializationException("Map key must be formatted as JsString, not '1'"))
    }
  }
  
  "The immutableSetFormat" should {
    val set = Set(1, 2, 3)
    val json = JsArray(JsNumber(1), JsNumber(2), JsNumber(3))
    "convert a Set[Int] to a JsArray of JsNumbers" in {
      set.toJson mustEqual json
    }
    "convert a JsArray of JsNumbers to a Set[Int]" in {
      json.fromJson[Set[Int]] mustEqual set 
    }
  }
  
  "The mutableSetFormat" should {
    val set = collection.mutable.Set(1, 2, 3)
    val json = JsArray(JsNumber(3), JsNumber(1), JsNumber(2))
    "convert a collection.mutable.Set[Int] to a JsArray of JsNumbers" in {
      set.toJson mustEqual json
    }
    "convert a JsArray of JsNumbers to a collection.mutable.Set[Int]" in {
      json.fromJson[collection.mutable.Set[Int]] mustEqual set 
    }
  }
  
}