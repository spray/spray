package cc.spray.can

import org.specs2._
import matcher.MustThrownMatchers
import scala.collection.mutable.ListBuffer

class LinkedListSpec extends Specification with MustThrownMatchers {

  case class Elem(value: Int) extends LinkedList.Element[Elem]

  def is =

  "A LinkedList should" ^
    "properly append elements"  ! e1^
    "properly remove elements"  ! e2^
    "properly refresh elements" ! e3


  def e1 = {
    val list = new LinkedList[Elem]
    list.toString mustEqual "[]"
    list.size mustEqual 0
    list += Elem(1)
    list.toString mustEqual "[Elem(1)]"
    list.size mustEqual 1
    list += Elem(2)
    list.toString mustEqual "[Elem(1), Elem(2)]"
    list.size mustEqual 2
    list += Elem(3)
    list.toString mustEqual "[Elem(1), Elem(2), Elem(3)]"
    list.size mustEqual 3
  }

  def e2 = {
    val list = new LinkedList[Elem]
    val e1 = Elem(1)
    val e2 = Elem(2)
    val e3 = Elem(3)
    list += e1
    list += e2
    list += e3
    list.toString mustEqual "[Elem(1), Elem(2), Elem(3)]"
    list.size mustEqual 3
    list -= e2
    list.toString mustEqual "[Elem(1), Elem(3)]"
    list.size mustEqual 2
    list -= e1
    list.toString mustEqual "[Elem(3)]"
    list.size mustEqual 1
    list -= e3
    list.toString mustEqual "[]"
    list.size mustEqual 0
  }

  def e3 = {
    val list = new LinkedList[Elem]
    list += Elem(1)
    val e2 = Elem(2)
    list += e2
    list += Elem(3)
    Thread.sleep(100)
    list.refresh(e2)
    val lb = new ListBuffer[Elem]
    list.forAllTimedOut(90)(lb.+=)
    lb.toString mustEqual "ListBuffer(Elem(1), Elem(3))"
    list.toString mustEqual "[Elem(1), Elem(3), Elem(2)]"
  }

}