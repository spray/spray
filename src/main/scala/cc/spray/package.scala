package cc

import java.io.File
import spray.http._
import collection.immutable.LinearSeq
import spray.marshalling.{Marshalling, Unmarshalling}
import spray.utils.{PimpedClass, PimpedLinearSeq}
import util.matching.Regex
import java.util.regex.Pattern

package object spray {

  type Route = RequestContext => Unit
  type ContentTypeResolver = (File, Option[Charset]) => ContentType
  type Marshaller[A] = (ContentType => Boolean) => Marshalling[A]
  type Unmarshaller[A] = ContentType => Unmarshalling[A]
  type RouteFilter[A] = RequestContext => FilterResult[A]
  
  private val unmanglingOperators = Map("$eq" -> "=", "$greater" -> ">", "$less" -> "<", "$plus" -> "+",
    "$minus" -> "-", "$times" -> "*", "$div" -> "/", "$bang" -> "!", "$at" -> "@", "$hash" -> "#", "$percent" -> "%",
    "$up" -> "^", "$amp" -> "&", "$tilde" -> "~", "$qmark" -> "?", "$bar" -> "|", "$bslash" -> "\\")
  
  def unmangle(name: String): String = (name /: unmanglingOperators) {
    case (n, (key, value)) => n.replace(key, value)
  }
  
  def make[A, U](a: A)(f: A => U): A = { f(a); a }
  
  def marshaller[T](implicit m: Marshaller[T]) = m
  def unmarshaller[T](implicit um: Unmarshaller[T]) = um
  
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