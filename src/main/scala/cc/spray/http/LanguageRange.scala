package cc.spray.http

sealed trait LanguageRange {
  def primaryTag: String
  def subTags: Seq[String]

  def value: String = (primaryTag +: subTags).mkString("-")
  
  override def toString = value
}

object LanguageRanges {
  
  case object `*` extends LanguageRange {
    def primaryTag = "*"
    def subTags = Seq.empty[String]
  }
  
  case class Language(primaryTag: String, subTags: String*) extends LanguageRange
  
}
