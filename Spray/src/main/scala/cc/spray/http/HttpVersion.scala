package cc.spray.http

import cc.spray.utils.ObjectRegistry

class HttpVersion private[http] (value: String) {
  override def toString = value
  HttpVersions.register(this, value)
}

object HttpVersions extends ObjectRegistry[String, HttpVersion] {
  val `HTTP/1.0` = new HttpVersion("HTTP/1.0")
  val `HTTP/1.1` = new HttpVersion("HTTP/1.1")
}
