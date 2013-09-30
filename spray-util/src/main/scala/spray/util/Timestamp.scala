package spray.util

import akka.util.duration._
import akka.util.Duration

private[spray] case class Timestamp private (timestampNanos: Long) {
  def +(period: Duration): Timestamp =
    if (isNever) this
    else if (!period.isFinite()) Timestamp.never
    else new Timestamp(timestampNanos + period.toNanos)

  def -(other: Timestamp): Duration =
    if (isNever) Duration.Inf
    else if (other.isNever) Duration.MinusInf
    else (timestampNanos - other.timestampNanos).nanos

  def isPast: Boolean = System.nanoTime() >= timestampNanos
  def isFuture: Boolean = !isPast

  def isFinite: Boolean = timestampNanos < Long.MaxValue
  def isNever: Boolean = timestampNanos == Long.MaxValue
}
private[spray] object Timestamp {
  def now: Timestamp = new Timestamp(System.nanoTime())
  val never: Timestamp = new Timestamp(Long.MaxValue)

  implicit val timestampOrdering: Ordering[Timestamp] = new Ordering[Timestamp] {
    def compare(x: Timestamp, y: Timestamp): Int =
      if (x.timestampNanos < y.timestampNanos) -1 else if (x.timestampNanos == y.timestampNanos) 0 else 1
  }
}

