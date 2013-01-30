package spray.io.openssl

import collection.immutable.Queue
import java.nio.ByteBuffer
import javax.net.ssl.SSLException
import annotation.tailrec
import spray.util.ConnectionCloseReasons
import spray.util._
import org.bridj.Pointer
import java.{util, lang}
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicInteger
import spray.io._
import spray.io.openssl.BridjedOpenssl._
import akka.event.LoggingAdapter


object OpenSslSupport {
  def apply(sslEnabled: PipelineContext => Boolean = _ => true, client: Boolean = true)(log: LoggingAdapter): PipelineStage =
    new PipelineStage {
      val ctx = SSLCtx.create(SSLv23_method())
      ctx.setDefaultVerifyPaths()
      ctx.setMode(SSL.SSL_MODE_RELEASE_BUFFERS)
      ctx.setOptions(SSL.SSL_OP_NO_COMPRESSION)

      {
        val buf = new DirectBuffer(10)
        buf.pointer.setCString("RSA")
        val res = ctx.setCipherList(buf)
        if (res != 1) {
            ERR_error_string(ERR_get_error(), buf.pointer)
            println("Error: "+buf.pointer.getCString)
        }
        println("Set result: "+res)
      }
      //ctx.setVerify(1)

      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        if (sslEnabled(context)) new SslPipelines(context, commandPL, eventPL)
        else Pipelines(commandPL, eventPL)

      final class SslPipelines(context: PipelineContext, commandPL: CPL, eventPL: EPL) extends CopyingBIOImpl with Pipelines {
        var handshakeInitialized = false
        var pendingSends = Queue.empty[ByteBuffer]
        var inputBuffer = Queue.empty[ByteBuffer]

        val ssl = ctx.newSSL()

        if (client)
          context.self ! StartHandshake
        else
          ssl.setAcceptState()

        val internal = BIO.fromImpl(this)
        ssl.setBio(internal, internal)

        // BIOImpl implementations, will be called when calling
        // ssl.read or ssl.write

        def write(buffer: Array[Byte]): Int = {
          debug("Should write %d bytes to network" format buffer.length)

          // TODO: another possibility would be to gather buffers to send
          // while executing an ssl call and send all data in one message
          // through the commandPL after handling a message
          commandPL(IOPeer.Send(ByteBuffer.wrap(buffer)))
          buffer.length
        }
        def read(buffer: Array[Byte], length: Int): Int = {
          if (inputBuffer.isEmpty) {
            debug("Should read %d bytes from network but nothing available yet" format length)
            -1
          } else {
            val first = inputBuffer.head
            val num = math.min(length, first.remaining())
            debug("Read %d bytes from incoming network queue" format length)
            first.get(buffer, 0, num)

            if (!first.hasRemaining)
              inputBuffer = inputBuffer.tail

            num
          }
        }
        def flush() {}

        val commandPipeline: CPL = {
          case x: IOPeer.Send =>
            debug("Should send %d bytes" format x.buffers.map(_.remaining()).sum)
            withTempBuf(encrypt(x.buffers))
            debug("Finished sending %d bytes" format x.buffers.map(_.remaining()).sum)

          //case x: IOPeer.Close =>

          case cmd => commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case StartHandshake =>
            debug("Starting handshake")
            ssl.connect()

          case IOPeer.Received(_, buffer) =>
            debug("Enqueing %d received bytes from network" format buffer.remaining)
            assert(buffer.position() < buffer.limit())
            inputBuffer = inputBuffer enqueue buffer
            withTempBuf(tryRead)
            debug("Finished receiving %d bytes" format buffer.remaining)

          //case x: IOPeer.Closed =>

          case ev => eventPL(ev)
        }

        def debug(str: String) {
          println("%d %4d %s" format (ssl.want, ssl.pending, str))
          //context.log.error("%d %4d %s" format (ssl.want, ssl.pending, str))
        }

        def encrypt(buffers: Seq[ByteBuffer])(direct: DirectBuffer) {
          @tailrec def trySend(buffer: ByteBuffer): Unit =
            if (pendingSends.nonEmpty) {
              debug("Enqueing buffer "+buffer.remaining())
              pendingSends = pendingSends enqueue buffer
            } else {
              val toWrite = buffer.remaining()

              debug("Trying to encrypt %d bytes" format toWrite)
              val copied = direct.setFromByteBuffer(buffer)

              val written = ssl.write(direct, copied)
              assert(written == copied, "Wrote %d but expected %d" format (written, copied))

              debug("Result "+written)
              if (copied < 0) {
                debug("Enqueing buffer "+buffer.limit()+" because of error "+ssl.getError(written))
                pendingSends = pendingSends enqueue buffer
              } else if (copied < toWrite)
                trySend(buffer)
            }

          buffers.foreach(trySend)
        }

        def tryRead(direct: DirectBuffer) {
          // we're reminding the open ssl state machine that there might
          // be work to do.
          val read = ssl.read(direct, 0)
          debug("Priming read returned %d" format read)
          if (read < 0)
            debug("Error was "+ssl.getError(read))

          checkPendingSSLOutput(direct)
        }

        @tailrec private[this] def checkPendingSSLOutput(direct: DirectBuffer) {
          val pendingBytes = ssl.pending
          if (pendingBytes > 0) {
            val read = ssl.read(direct, pendingBytes)
            debug("Read %d bytes of decrypted data (pending was %d)" format (read, pendingBytes))

            if (read > 0) {
              val buffer = direct.copyToByteBuffer(read)

              //val str = new String(buffer.array(), 0, read)
              //debug("'%s' .. '%s'" format (str.take(15).mkString, str.takeRight(15).mkString))

              eventPL(IOPeer.Received(context.connection, buffer))

              checkPendingSSLOutput(direct)
            }
            else if (read == -1)
              debug("SSL_read returned "+ssl.getError(-1))
          } else if (pendingSends.nonEmpty) {
            val sends = pendingSends.toList
            pendingSends = Queue.empty
            encrypt(sends)(direct)
          }
        }

        def withTempBuf(f: DirectBuffer => Unit) {
          val tempBuf = DirectBufferPool.acquire()
          try f(tempBuf)
          catch {
            case e: RuntimeException =>
              log.error(e, "Closing encrypted connection to {} due to {}", context.connection.remoteAddress, e)
              commandPL(IOPeer.Close(ConnectionCloseReasons.ProtocolError(e.toString)))
          }
          finally DirectBufferPool.release(tempBuf)
        }
      }
    }

  case object StartHandshake extends Event

  object DirectBufferPool extends SslBufferPool[DirectBuffer] {
    // we feed ssl with chunks of 16384 because that seems to be the
    // size it breaks packets into anyway
    override val BufferSize: Int = 16384
    val num = new AtomicInteger(0)

    def allocate(capacity: Int): DirectBuffer = {
      val newNum = num.incrementAndGet()
      println("%2d buffers" format newNum)
      new DirectBuffer(capacity)
    }

    def refurbish(buffer: DirectBuffer) {}
  }
}
