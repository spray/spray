package cc.spray.marshalling

import cc.spray.http.{BufferContent, ContentTypeRange}

sealed trait Unmarshalling[A]
case class CantUnmarshal[A](onlyFrom: List[ContentTypeRange]) extends Unmarshalling[A]
case class UnmarshalWith[A](f: BufferContent => A) extends Unmarshalling[A] 