package cc.spray.can

import org.slf4j.LoggerFactory
import cc.spray.nio._
import akka.actor.{Scheduler, Actor}
import java.util.concurrent.TimeUnit

class NewHttpServer(val config: ServerConfig = ServerConfig.fromAkkaConf,
                    val nioWorker: NioWorker = NioWorker) extends Actor with NewHttpServerConnectionActorComponent {

  val startRequestParser = new EmptyRequestParser(config.parserConfig)
  lazy val serviceActor = actor(config.serviceActorId)
  lazy val timeoutActor = actor(config.timeoutActorId)
  private val log = LoggerFactory.getLogger(getClass)
  private var bindingKey: Option[Key] = None

  protected val requestTimeoutCycle = if (config.requestTimeout == 0) None else Some {
    Scheduler.schedule(() => self ! HandleTimedOutRequests, config.timeoutCycle, config.timeoutCycle, TimeUnit.MILLISECONDS)
  }

  override def preStart() {
    log.info("Starting spray-can HTTP server on {}", config.endpoint)
    nioWorker ! Bind(self, new ConnectionActor(_), config.endpoint)
  }

  override def postStop() {
    bindingKey.map { key =>
      log.info("Stopping spray-can HTTP server on {}", config.endpoint)
      val receiver = Actor.actorOf(new Actor {
        protected def receive = {
          case _: Unbound =>
            log.info("Stopped spray-can HTTP server on {}", config.endpoint)
            self.stop()
        }
      })
      nioWorker ! Unbind(receiver.start(), key)
    }
  }

  protected def receive = {
    case Bound(key) =>
      bindingKey = Some(key)
      log.info("spray-can HTTP server started on {}", config.endpoint)
    case HandleTimedOutRequests =>
  }

}