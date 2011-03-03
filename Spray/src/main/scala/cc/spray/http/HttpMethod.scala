package cc.spray.http

import cc.spray.utils.ObjectRegistry

class HttpMethod private[http] (val value: String) {
  override def toString = value
  
  HttpMethods.register(this, value)
}

object HttpMethods extends ObjectRegistry[String, HttpMethod] {
  val GET     = new HttpMethod("GET")
  val PUT     = new HttpMethod("PUT")
  val POST    = new HttpMethod("POST")
  val DELETE  = new HttpMethod("DELETE")
  val PATCH   = new HttpMethod("PATCH")
  val OPTIONS = new HttpMethod("OPTIONS")
  val HEAD    = new HttpMethod("HEAD")
  val CONNECT = new HttpMethod("CONNECT")
  val TRACE   = new HttpMethod("TRACE")
}
