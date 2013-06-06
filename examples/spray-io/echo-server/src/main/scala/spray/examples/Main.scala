package spray.examples

import java.net.InetSocketAddress
import scala.collection.immutable.Queue
import akka.util.duration._
import akka.pattern.ask
import akka.io.{Tcp, IO}
import akka.util.{ByteString, Timeout}
import akka.actor._
import spray.util._

object Main extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("echo-server")

  // and our actual server "service" actor
  val server = system.actorOf(Props(new EchoServer), name = "echo-server")

  // we bind the server to a port on localhost and hook
  // in a continuation that informs us when bound
  val endpoint = new InetSocketAddress("localhost", 23456)
  implicit val bindingTimeout = Timeout(1.second)
  import system.dispatcher // execution context for the future

  val boundFuture = IO(Tcp) ? Tcp.Bind(server, endpoint)

  boundFuture.onSuccess { case Tcp.Bound(address) =>
    println("\nBound echo-server to " + address)
    println("Run `telnet localhost 23456`, type something and press RETURN. Type `STOP` to exit...\n")
  }
}

class EchoServer extends Actor with SprayActorLogging {
  var childrenCount = 0

  def receive = {
    case Tcp.Connected(_, _) =>
      val tcpConnection = sender
      val newChild = context.watch(context.actorOf(Props(new EchoServerConnection(tcpConnection))))
      childrenCount += 1
      sender ! Tcp.Register(newChild)
      log.debug("Registered for new connection")

    case Terminated(_) if childrenCount > 0 =>
      childrenCount -= 1
      log.debug("Connection handler stopped, another {} connections open", childrenCount)

    case Terminated(_) =>
      log.debug("Last connection handler stopped, shutting down")
      context.system.shutdown()
  }
}

class EchoServerConnection(tcpConnection: ActorRef) extends Actor with SprayActorLogging {
  context.watch(tcpConnection)

  def receive = idle

  def idle: Receive = stopOnConnectionTermination orElse {
    case Tcp.Received(data) if data.utf8String.trim == "STOP" =>
      log.info("Shutting down")
      tcpConnection ! Tcp.Write(ByteString("Shutting down...\n"))
      tcpConnection ! Tcp.Close

    case Tcp.Received(data) =>
      tcpConnection ! Tcp.Write(data, ack = SentOk)
      context.become(waitingForAck)

    case x: Tcp.ConnectionClosed =>
      log.debug("Connection closed: {}", x)
      context.stop(self)
  }

  def waitingForAck: Receive = stopOnConnectionTermination orElse {
    case Tcp.Received(data) =>
      tcpConnection ! Tcp.SuspendReading
      context.become(waitingForAckWithQueuedData(Queue(data)))

    case SentOk =>
      context.become(idle)

    case x: Tcp.ConnectionClosed =>
      log.debug("Connection closed: {}, waiting for pending ACK", x)
      context.become(waitingForAckWithQueuedData(Queue.empty, closed = true))
  }

  def waitingForAckWithQueuedData(queuedData: Queue[ByteString], closed: Boolean = false): Receive =
    stopOnConnectionTermination orElse {
      case Tcp.Received(data) =>
        context.become(waitingForAckWithQueuedData(queuedData.enqueue(data)))

      case SentOk if queuedData.isEmpty && closed =>
        log.debug("No more pending ACKs, stopping")
        tcpConnection ! Tcp.Close
        context.stop(self)

      case SentOk if queuedData.isEmpty =>
        tcpConnection ! Tcp.ResumeReading
        context.become(idle)

      case SentOk =>
        // for brevity we don't interpret STOP commands here
        tcpConnection ! Tcp.Write(queuedData.head, ack = SentOk)
        context.become(waitingForAckWithQueuedData(queuedData.tail, closed))

      case x: Tcp.ConnectionClosed =>
        log.debug("Connection closed: {}, waiting for completion of {} pending writes", x, queuedData.size)
        context.become(waitingForAckWithQueuedData(queuedData, closed = true))
    }

  def stopOnConnectionTermination: Receive = {
    case Terminated(`tcpConnection`) =>
      log.debug("TCP connection actor terminated, stopping...")
      context.stop(self)
  }
  
  object SentOk extends Tcp.Event
}
