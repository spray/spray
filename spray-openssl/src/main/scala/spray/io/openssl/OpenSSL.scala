package spray.io.openssl

import org.bridj.{JNI, BridJ, TypedPointer, Pointer}
import java.nio.ByteBuffer

import BridjedOpenssl._
import BIO_METHOD._
import java.util.concurrent.locks.ReentrantLock
import spray.io.openssl
import java.lang
import org.bridj.util.JNIUtils

class DirectBuffer(_size: Int) {
  val pointer = Pointer.allocateBytes(size)

  def address: Long = pointer.getPeer
  //def asByteBuffer: ByteBuffer = pointer.getByteBuffer
  def get(buffer: Array[Byte]) {
    pointer.getBytes(buffer)
  }
  def get(buffer: Array[Byte], size: Int) {
    pointer.getBytesAtOffset(0, buffer, 0, size)
  }
  def set(buffer: Array[Byte]) {
    pointer.setBytes(buffer)
  }

  /**
   *  Copies as much remaining bytes from the ByteBuffer into this direct buffer.
   *  Note: This does not change the position of the byte buffer. You have to adjust
   *  this manually.
   */
  def setFromByteBuffer(buffer: ByteBuffer): Int = {
    val numBytes = math.min(_size, buffer.remaining())
    pointer.setBytesAtOffset(0, buffer, buffer.position(), numBytes)
    numBytes
  }

  def copyToByteBuffer(size: Int): ByteBuffer = {
    val bytes = new Array[Byte](size)
    get(bytes, size)

    ByteBuffer.wrap(bytes)
  }

  def size: Int = _size
}

class BIO(pointer: Long) extends TypedPointer(pointer) {
  def write(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    BIO_write(getPeer, buffer.address, len)
  }
  def read(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    BIO_read(getPeer, buffer.address, len)
  }
  def ctrlPending: Int =
    BIO_ctrl_pending(getPeer)

}

/**
 * A scala side correspondence to BIO_METHOD
 */
trait BIOImpl {
  def read(buffer: Pointer[_], length: Int): Int
  def write(buffer: Pointer[_], length: Int): Int
  def flush(): Unit
}
abstract class CopyingBIOImpl extends BIOImpl {
  def read(buffer: Array[Byte], length: Int): Int
  def write(buffer: Array[Byte]): Int

  def read(buffer: Pointer[_], length: Int): Int = {
    val buf = new Array[Byte](length)
    val numRead = read(buf, length)
    if (numRead > 0)
      buffer.setBytesAtOffset(0, buf, 0, numRead)

    numRead
  }
  def write(buffer: Pointer[_], length: Int): Int =
    write(buffer.getBytes(length))
}

object BIO {
  /*def newPair(): (BIO, BIO) = {
    val p1 = Pointer.allocateLong()
    val p2 = Pointer.allocateLong()
    BIO_new_bio_pair(p1, 0, p2, 0)
    (new BIO(p1.get()), new BIO(p2.get()))
  }*/

  def fromImpl(impl: BIOImpl): BIO = {
    val bio = fromMethod(theMethod)
    registerImpl(bio, impl)
    bio
  }
  def fromMethod(m: BIO_METHOD): BIO =
    new BIO(BIO_new(Pointer.pointerTo(m).getPeer))

  /**
   * Save an implementation for one of our bios. This puts a reference to the implementation into
   * the `bio->ptr` member.
   *
   * FIXME: unregister global ref
   */
  def registerImpl(bio: BIO, impl: BIOImpl): Unit =
    new bio_st(Pointer.pointerToAddress(bio.getPeer, classOf[bio_st])).ptr(Pointer.pointerToAddress(JNI.newGlobalRef(impl)))

  def implForBIO(bioPtr: Long): BIOImpl =
    JNI.refToObject(new bio_st(Pointer.pointerToAddress(bioPtr, classOf[bio_st])).ptr().getPeer).asInstanceOf[BIOImpl]

  val createCB =
    new create_callback {
      def apply(BIOPtr1: Long): Int = {
        //println("Called with "+BIOPtr1)
        new bio_st(Pointer.pointerToAddress(BIOPtr1, classOf[bio_st])).init(1)
        1
      }
    }
  val readCB =
    new bread_callback {
      def apply(BIOPtr1: Long, charPtr1: Pointer[java.lang.Byte], int1: Int): Int = {
        val res = implForBIO(BIOPtr1).read(charPtr1, int1)
        if (res == -1)
          BIO_set_flags(BIOPtr1, 9/*BIO_FLAGS_READ|BIO_FLAGS_SHOULD_RETRY*/)
        res
      }
    }
  val writeCB =
    new bwrite_callback {
      def apply(BIOPtr1: Long, charPtr1: Pointer[java.lang.Byte], int1: Int): Int =
        implForBIO(BIOPtr1).write(charPtr1, int1)
    }
  val ctrlCB =
    new ctrl_callback {
      def apply(BIOPtr1: Long, cmd: Int, l1: Long, voidPtr1: Pointer[_]): Long = cmd match {
        case BIO_CTRL_FLUSH =>
          implForBIO(BIOPtr1).flush()
          1
        case _ => 0
      }
    }

  val theMethod = createMethod()

  private[this] def createMethod() =
    (new BIO_METHOD)
      .create(createCB.toPointer)
      .bread(readCB.toPointer)
      .bwrite(writeCB.toPointer)
      .ctrl(ctrlCB.toPointer)
}
class SSLCtx(pointer: Long) extends TypedPointer(pointer) {
  def newSSL(): SSL = {
    val ssl = SSL_new(getPeer)
    require(ssl != 0L)
    new SSL(ssl)
  }

  def setDefaultVerifyPaths(): Int =
    SSL_CTX_set_default_verify_paths(getPeer)

  def setVerify(mode: Int) {
    SSL_CTX_set_verify(getPeer, mode, 0)
  }

  def setCipherList(ciphers: DirectBuffer): Int =
    SSL_CTX_set_cipher_list(getPeer, ciphers.pointer.getPeer)

  def usePrivateKeyFile(fileName: String, `type`: Int): Int = {
    val buffer = Pointer.allocateBytes(fileName.length + 1)
    buffer.setCString(fileName)
    SSL_CTX_use_PrivateKey_file(getPeer, buffer, `type`)
  }
  def useCertificateChainFile(fileName: String): Int = {
    val buffer = Pointer.allocateBytes(fileName.length + 1)
    buffer.setCString(fileName)
    SSL_CTX_use_certificate_chain_file(getPeer, buffer)
  }

  import SSL._
  def setOptions(options: Long): Long =
    SSL_CTX_ctrl(getPeer, SSL_CTRL_OPTIONS, options, 0)

  def setMode(mode: Long): Long =
    SSL_CTX_ctrl(getPeer, SSL_CTRL_MODE, mode, 0)

}
object SSLCtx {
  def create(method: Long): SSLCtx =
    new SSLCtx(SSL_CTX_new(method))

  // make sure openssl is initialized
  OpenSSL()
}
class SSL(pointer: Long) extends TypedPointer(pointer) {
  def setBio(readBio: BIO, writeBio: BIO) {
    SSL_set_bio(getPeer, readBio.getPeer, writeBio.getPeer)
  }

  def connect(): Int =
    SSL_connect(getPeer)

  def accept(): Int =
    SSL_accept(getPeer)

  def setAcceptState(): Unit =
    SSL_set_accept_state(getPeer)


  def write(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    SSL_write(getPeer, buffer.address, len)
  }
  def read(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    SSL_read(getPeer, buffer.address, len)
  }

  def want: Int =
    SSL_want(getPeer)

  def pending: Int =
    SSL_pending(getPeer)

  def getError(ret: Int): Int =
    SSL_get_error(getPeer, ret)


  def setCallback(f: (Int, Int) => Unit) {
    // FIXME: make sure the callback is not GC'd
    val cb = new InfoCallback {
      def apply(ssl: Long, where: Int, ret: Int): Unit = f(where, ret)
    }
    JNI.newGlobalRef(cb)
    SSL_set_info_callback(getPeer, cb.toPointer)
  }
}

object SSL {
  val SSL_CTRL_OPTIONS = 32
  val SSL_CTRL_MODE = 33

  val SSL_MODE_RELEASE_BUFFERS = 0x00000010L
  val SSL_OP_NO_COMPRESSION = 0x00020000L
}

object OpenSSL {
  def apply(){}

  SSL_library_init()
  SSL_load_error_strings()
  val locks = {
    val num = CRYPTO_num_locks()
    println("Creating %d locks for openssl" format num)
    Array.fill(num)(new ReentrantLock)
  }
  val lockingCB = new LockingCB {
    def apply(mode: Int, `type`: Int, file: Pointer[lang.Byte], line: Int) {
      val lock = locks(`type`)
      if ((mode & 1) != 0)
        lock.lock()
      else
        lock.unlock()
    }
  }
  val threadIdCB = new ThreadIdCB {
    def apply(CRYPTO_THREADIDPtr1: Long) {
      CRYPTO_THREADID_set_numeric(CRYPTO_THREADIDPtr1, Thread.currentThread().getId)
    }
  }
  CRYPTO_set_locking_callback(lockingCB.toPointer)
  CRYPTO_THREADID_set_callback(threadIdCB.toPointer)

  def lastErrorString: String = {
    val resPtr = Pointer.allocateBytes(200)
    ERR_error_string_n(ERR_get_error(), resPtr, 200)
    resPtr.getCString
  }
}
