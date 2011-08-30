package cc.spray.can.utils

import org.specs2.mutable._

class LinkedListSpec extends Specification {

  case class Elem(value: Int) extends LinkedList.Element[Elem]

  "A LinkedList" should {
    "properly append elements" in {
      val list = new LinkedList[Elem]
      list.toString mustEqual "[]"
      list.size mustEqual 0
      list += Elem(1)
      list.toString mustEqual "[Elem(1)]"
      list.size mustEqual 1
      list += Elem(2)
      list.toString mustEqual "[Elem(1),Elem(2)]"
      list.size mustEqual 2
      list += Elem(3)
      list.toString mustEqual "[Elem(1),Elem(2),Elem(3)]"
      list.size mustEqual 3
    }
    "properly remove elements" in {
      val list = new LinkedList[Elem]
      val e1 = Elem(1)
      val e2 = Elem(2)
      val e3 = Elem(3)
      list += e1
      list += e2
      list += e3
      list.toString mustEqual "[Elem(1),Elem(2),Elem(3)]"
      list.size mustEqual 3
      list -= e2
      list.toString mustEqual "[Elem(1),Elem(3)]"
      list.size mustEqual 2
      list -= e1
      list.toString mustEqual "[Elem(3)]"
      list.size mustEqual 1
      list -= e3
      list.toString mustEqual "[]"
      list.size mustEqual 0
    }
    "properly move elements to the end" in {
      val list = new LinkedList[Elem]
      val e1 = Elem(1)
      val e2 = Elem(2)
      val e3 = Elem(3)
      list += e1
      list += e2
      list += e3
      list.toString mustEqual "[Elem(1),Elem(2),Elem(3)]"
      list.refresh(e2)
      list.toString mustEqual "[Elem(1),Elem(3),Elem(2)]"
      list.refresh(e1)
      list.toString mustEqual "[Elem(3),Elem(2),Elem(1)]"
      list.refresh(e1)
      list.toString mustEqual "[Elem(3),Elem(2),Elem(1)]"
    }
  }

}