package spray.util

import java.util.concurrent.atomic.AtomicLong

// see http://mechanical-sympathy.blogspot.it/2011/08/false-sharing-java-7.html
//TODO: remove after upgrade to Java 7 + JSR166e LongAdder
class PaddedAtomicLong(value: Long = 0) extends AtomicLong(value) {
  @volatile var p1, p2, p3, p4, p5, p6 = 7L

  protected def sumPaddingToPreventOptimisation() = p1 + p2 + p3 + p4 + p5 + p6
}
