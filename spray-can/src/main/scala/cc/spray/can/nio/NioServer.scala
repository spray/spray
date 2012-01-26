package cc.spray.can.nio

import akka.actor.Actor
import org.slf4j.LoggerFactory
import cc.spray.can.config.NioServerConfig
import java.net.InetSocketAddress

abstract class NioServer(val config: NioServerConfig, val nioWorker: NioWorker = NioWorker) extends Actor {
  private val log = LoggerFactory.getLogger(getClass)
  private val endpoint = new InetSocketAddress(config.host, config.port)
  private var bindingKey: Option[Key] = None

  override def preStart() {
    log.info("Starting {} on {}", config.serverName, endpoint)
    nioWorker ! Bind(
      handleCreator = self,
      address = endpoint,
      backlog = config.bindingBacklog,
      ackTo = Some(self)
    )
  }

  override def postStop() {
    for (key <- bindingKey) {
      log.info("Stopping {} on {}", config.serverName, endpoint)
      nioWorker ! Unbind(key)
    }
  }

  protected def receive = {
    case Bound(key) =>
      bindingKey = Some(key)
      log.info("{} started on {}", config.serverName, endpoint)
    case Connected(key) =>
      nioWorker ! Register(createConnectionHandle(key))
  }

  protected def createConnectionHandle(key: Key): Handle
}