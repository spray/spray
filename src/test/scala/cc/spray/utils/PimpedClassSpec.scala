package cc.spray
package utils

import org.specs.Specification
import collection.LinearSeqOptimized
import java.util.{List => JList}

class PimpedClassSpec extends Specification {
  
  "getTypeArgumentsOf" should {
    "be able to resolve the argument to a (class) type constructor with one type parameters" in {
      (new TestClass1[String]{}).getClass
              .getTypeArgumentsOf(classOf[TestClass1[_]]) mustEqual
      List(Some(classOf[String]))
    }
    "be able to resolve the arguments to a (class) type constructor with two type parameters" in {
      (new TestClass2[Option[Int], String]{}).getClass
              .getTypeArgumentsOf(classOf[TestClass2[_, _]]) mustEqual
      List(Some(classOf[Option[_]]), Some(classOf[String]))
    }
    "be able to resolve the arguments to a (class) type constructor with three type parameters" in {
      (new TestClass3[String, Function1[Int, Int], Object]{}).getClass
              .getTypeArgumentsOf(classOf[TestClass3[_, _, _]]) mustEqual
      List(Some(classOf[String]), Some(classOf[Function1[_, _]]), Some(classOf[Object]))
    }
    "be able to resolve the arguments to an (interface) type constructor with two type parameters" in {
      classOf[List[String]].getTypeArgumentsOf(classOf[LinearSeqOptimized[_, _]]) mustEqual
      List(None, Some(classOf[List[_]]))
    }
    "be able to resolve the arguments to an (interface) type constructor two levels up" in {
      classOf[Bar].getTypeArgumentsOf(classOf[JList[_]]) mustEqual
      List(Some(classOf[String]))
    }
  }
  
}

class TestClass1[A]
class TestClass2[A, B]
class TestClass3[A, B, C]

trait Foo[A] extends JList[A]
trait Bar extends Foo[String]