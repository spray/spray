package spray.util

import scala.concurrent.duration._

private[spray] class Timestamp private (val timestampNanos: Long) extends AnyVal {
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
  def never: Timestamp = new Timestamp(Long.MaxValue)

  implicit val timestampOrdering: Ordering[Timestamp] = new Ordering[Timestamp] {
    def compare(x: Timestamp, y: Timestamp): Int = java.lang.Long.compare(x.timestampNanos, y.timestampNanos)
  }
}

