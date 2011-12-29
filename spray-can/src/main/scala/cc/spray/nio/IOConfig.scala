package cc.spray.nio

import java.net.InetSocketAddress

case class IOConfig(
  threadName: String = "spray-nio-actor",
  readBufferSize: Int = 8192,
  serverAddress: Option[InetSocketAddress] = None
)