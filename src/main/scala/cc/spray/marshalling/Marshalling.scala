package cc.spray.marshalling

import cc.spray.http._

sealed trait Marshalling[-A]
case class CantMarshal(onlyTo: List[ContentType]) extends Marshalling[Any]
case class MarshalWith[-A](f: A => HttpContent) extends Marshalling[A] 

sealed trait Unmarshalling[+A]
case class CantUnmarshal(onlyFrom: List[ContentTypeRange]) extends Unmarshalling[Nothing]
case class UnmarshalWith[+A](f: HttpContent => A) extends Unmarshalling[A] 