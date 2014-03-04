package spray.http

sealed trait RangeUnit extends ValueRenderable
object BytesUnit extends RangeUnit { def render[R <: Rendering](r: R): r.type = r ~~ "bytes" }
case class OtherRangeUnit(name: String) extends RangeUnit {
  def render[R <: Rendering](r: R): r.type = r ~~ name
}
