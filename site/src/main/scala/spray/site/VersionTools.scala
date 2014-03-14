package spray.site

object VersionTools {
  sealed trait Version
  case class FinalVersion(version: String) extends Version
  case class SnapshotVersion(version: String) extends Version
  case class SuffixedVersion(version: String, suffix: String) extends Version
  case class UnknownVersion(name: String) extends Version

  def parseVersion(name: String) = name match {
    case FinalVersionP(version)            ⇒ FinalVersion(version)
    case SnapshotVersionP(version)         ⇒ SnapshotVersion(version)
    case SuffixedVersionP(version, suffix) ⇒ SuffixedVersion(version, suffix)
    case _                                 ⇒ UnknownVersion(name)
  }

  private val FinalVersionP = """(\d+\.\d+\.\d+).*""".r
  private val SnapshotVersionP = """(.+)-SNAPSHOT""".r
  private val SuffixedVersionP = """([^-]+)-(.+)""".r
}

trait ReversedOrdering
object ReversedOrdering {
  implicit def reversedStringOrdering: Ordering[String with ReversedOrdering] =
    Ordering.String.reverse.asInstanceOf[Ordering[String with ReversedOrdering]]
  implicit def stringIsReversed(str: String): String with ReversedOrdering =
    str.asInstanceOf[String with ReversedOrdering]
}
