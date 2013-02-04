package spray.io.openssl

import org.bridj.{JNI, Pointer, TypedPointer}
import spray.io.openssl.BridjedOpenssl._
import spray.io.openssl.BIO_METHOD.{ctrl_callback, bwrite_callback, bread_callback, create_callback}
import java.io.{ByteArrayInputStream, InputStream}

class BIO private[openssl](pointer: Long) extends TypedPointer(pointer) {
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
    val bio = fromMethod(javaMethod)
    registerImpl(bio, impl)
    bio
  }

  def fromInputStream(is: InputStream): BIO =
    fromImpl(new CopyingBIOImpl {
      def flush() {}
      def write(buffer: Array[Byte]): Int =
        throw new UnsupportedOperationException("writing not supported")
      def read(buffer: Array[Byte], length: Int): Int =
        is.read(buffer, 0, length)
    })
  def fromBytes(bytes: Array[Byte]): BIO =
    fromInputStream(new ByteArrayInputStream(bytes))

  def fromMethod(m: BIO_METHOD): BIO =
    new BIO(BIO_new(Pointer.pointerTo(m).getPeer))

  /**
   * Save an implementation for one of our bios. This puts a reference to the implementation into
   * the `bio->ptr` member.
   */
  def registerImpl(bio: BIO, impl: BIOImpl): Unit = {
    val globalRef = BIOReferenceManager.globalReference(bio, impl)
    new bio_st(Pointer.pointerToAddress(bio.getPeer, classOf[bio_st]))
      .ptr(Pointer.pointerToAddress(globalRef))
  }

  /**
   * Manages global native references. It binds the lifetime of the
   * global reference to the lifetime of the generated BIO: When the
   * generated BIO gets garbage collected,
   *   - the bio instance itself will be freed
   *   - the associated global ref to the implementation callback will be released
   */
  object BIOReferenceManager extends ReferenceQueue[BIO] {
    //val queue = new ReferenceQueue[BIO]
    var bios: List[BIOEntry] = Nil

    case class BIOEntry(ref: PhantomReference[BIO], globalRef: Long, bioPtr: Long, impl: BIOImpl)

    def globalReference(bio: BIO, impl: BIOImpl): Long = {
      val globalRef = JNI.newGlobalRef(impl)
      synchronized {
        bios ::= BIOEntry(new PhantomReference[BIO](bio, this), globalRef, bio.getPeer, impl)
      }
      globalRef
    }

    /**
     * Try to free bios if they aren't referenced any more.
     */
    def cleanup(): Unit = synchronized {
      val (deadBios, aliveBios) = bios.partition(_.ref.isEnqueued())

      deadBios.foreach {
        case BIOEntry(_, globalRef, bioPtr, impl) =>
          OpenSSL.checkResult(BIO_free(bioPtr))
          JNI.deleteGlobalRef(globalRef)
      }
      bios = aliveBios
    }
  }

  def implForBIO(bioPtr: Long): BIOImpl =
    JNI.refToObject(new bio_st(Pointer.pointerToAddress(bioPtr, classOf[bio_st])).ptr().getPeer)
       .asInstanceOf[BIOImpl]

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
        if (res == -1) BIO_set_flags(BIOPtr1, BIO_FLAGS_READ | BIO_FLAGS_SHOULD_RETRY)
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

  val javaMethod =
    (new BIO_METHOD)
      .create(createCB.toPointer)
      .bread(readCB.toPointer)
      .bwrite(writeCB.toPointer)
      .ctrl(ctrlCB.toPointer)

  val BIO_FLAGS_READ = 1
  val BIO_FLAGS_SHOULD_RETRY = 8
}
