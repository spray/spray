package cc.spray.http

import java.net.InetAddress

case class HttpIp(ip: InetAddress) {
  def value: String = ip.getHostAddress
  override def toString = value
}

object HttpIp {
  implicit def string2HttpIp(s: String): HttpIp = HttpIp(InetAddress.getByName(s))
}