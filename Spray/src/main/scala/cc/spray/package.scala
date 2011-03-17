package cc

import java.io.File
import spray.http._
import collection.immutable.LinearSeq
import spray.utils.{PimpedClass, PimpedLinearSeq}
import util.matching.Regex
import java.util.regex.Pattern

package object spray {

  type Route = RequestContext => Unit
  type RoutingResult = Either[Set[Rejection], HttpResponse]
  type ContentTypeResolver = (File, Option[Charset]) => ContentType
  type Marshaller = PartialFunction[Any, (List[ContentType], ContentType => RawContent)]
  type Unmarshaller[A] = ContentType => Either[List[ContentTypeRange], BufferContent => A]
  type RouteFilter0 = RequestContext => Option[List[Rejection]]
  type RouteFilter1 = RouteFilter[String]
  type RouteFilter2 = RouteFilter[(String, String)]
  type RouteFilter3 = RouteFilter[(String, String, String)]
  type RouteFilter4 = RouteFilter[(String, String, String, String)]
  type RouteFilter5 = RouteFilter[(String, String, String, String, String)]
  type RouteFilter[T] = RequestContext => Either[List[Rejection], T]
  
  private val unmanglingOperators = Map("$eq" -> "=", "$greater" -> ">", "$less" -> "<", "$plus" -> "+",
    "$minus" -> "-", "$times" -> "*", "$div" -> "/", "$bang" -> "!", "$at" -> "@", "$hash" -> "#", "$percent" -> "%",
    "$up" -> "^", "$amp" -> "&", "$tilde" -> "~", "$qmark" -> "?", "$bar" -> "|", "$bslash" -> "\\")
  
  def unmangle(name: String): String = (name /: unmanglingOperators) {
    case (n, (key, value)) => n.replace(key, value)
  }
  
  def make[A, U](a: A)(f: A => U): A = { f(a); a }
  
  
  // implicits
  
  
  implicit def pimpLinearSeq[A](seq: LinearSeq[A]): PimpedLinearSeq[A] = new PimpedLinearSeq[A](seq)

  implicit def pimpClass[A](clazz: Class[A]): PimpedClass[A] = new PimpedClass[A](clazz)

  implicit def pimpFile(file: File) = new {
    def extension = {
      val name = file.getName
      name.lastIndexOf('.') match {
        case -1 => ""
        case x => name.substring(x + 1)
      }
    }
  }
  
  implicit def pimpRegex(regex: Regex) = new {
    def groupCount = {
      try {
        val field = classOf[Pattern].getDeclaredField("capturingGroupCount")
        field.setAccessible(true)
        field.getInt(regex.pattern) - 1
      } catch {
        case t: Throwable =>
          throw new RuntimeException("Could not determine regex group count: " + regex.pattern.pattern, t)
      }
    }
  } 
}