/*
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.util
package pimps

import org.specs2.mutable._
import collection.LinearSeqOptimized
import java.util.{List => JList}

class PimpedClassSpec extends Specification {
  
  "getTypeArgumentsOf" should {
    "be able to resolve the argument to a (class) type constructor with one type parameters" in {
      (new TestClass1[String]{}).getClass
              .getTypeArgumentsOf(classOf[TestClass1[_]]) ===
      List(Some(classOf[String]))
    }
    "be able to resolve the arguments to a (class) type constructor with two type parameters" in {
      (new TestClass2[Option[Int], String]{}).getClass
              .getTypeArgumentsOf(classOf[TestClass2[_, _]]) ===
      List(Some(classOf[Option[_]]), Some(classOf[String]))
    }
    "be able to resolve the arguments to a (class) type constructor with three type parameters" in {
      (new TestClass3[String, Function1[Int, Int], Object]{}).getClass
              .getTypeArgumentsOf(classOf[TestClass3[_, _, _]]) ===
      List(Some(classOf[String]), Some(classOf[Function1[_, _]]), Some(classOf[Object]))
    }
    "be able to resolve the arguments to an (interface) type constructor with two type parameters" in {
      classOf[List[String]].getTypeArgumentsOf(classOf[LinearSeqOptimized[_, _]]) ===
      List(None, Some(classOf[List[_]]))
    }
    "be able to resolve the arguments to an (interface) type constructor two levels up" in {
      classOf[Bar].getTypeArgumentsOf(classOf[JList[_]]) ===
      List(Some(classOf[String]))
    }
  }
  
}

class TestClass1[A]
class TestClass2[A, B]
class TestClass3[A, B, C]

trait Foo[A] extends JList[A]
trait Bar extends Foo[String]