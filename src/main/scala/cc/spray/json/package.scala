package cc.spray

import json.formats.{JsonReader, JsonWriter}

package object json {
  
  def jsonReader[T](implicit reader: JsonReader[T]) = reader  
  def jsonWriter[T](implicit writer: JsonWriter[T]) = writer 
  
  implicit def pimpAny[T :JsonWriter](any: T): PimpedAny[T] = new PimpedAny(any, jsonWriter) 
  
}