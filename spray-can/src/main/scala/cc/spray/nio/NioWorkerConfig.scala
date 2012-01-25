package cc.spray.nio

case class NioWorkerConfig(
  threadName: String = "spray-nio-worker",
  readBufferSize: Int = 4096
)