package cc.spray.http
package parser

private[parser] trait CommonActions {
  
  def getMediaType(mainType: String, subType: String): MediaType = {
    val value = mainType.toLowerCase + "/" + subType.toLowerCase
    MediaTypes.getForKey(value).getOrElse(MediaTypes.CustomMediaType(value))
  }
  
}