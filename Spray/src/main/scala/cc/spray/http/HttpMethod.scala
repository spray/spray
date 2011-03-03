package cc.spray.http

import cc.spray.utils.ObjectRegistry

class HttpMethod private[http] (val value: String) {
  override def toString = value
  
  HttpMethods.register(this, value)
}

object HttpMethods extends ObjectRegistry[String, HttpMethod] {
  val DELETE  = new HttpMethod("DELETE")
  val GET     = new HttpMethod("GET")
  val HEAD    = new HttpMethod("HEAD")
  val OPTIONS = new HttpMethod("OPTIONS")
  val POST    = new HttpMethod("POST")
  val PUT     = new HttpMethod("PUT")
  val TRACE   = new HttpMethod("TRACE")
}
