package cc.spray.http
package parser

trait CommonActions {
  
  def getMediaType(mainType: String, subType: String): MediaType = {
    val value = mainType.toLowerCase + "/" + subType.toLowerCase
    MediaTypes.get(value).getOrElse(MediaTypes.CustomMediaType(value))
  }
  
}