package cc.spray.http
package parser

trait CommonActions {
  
  def getMimeType(mainType: String, subType: String): MimeType = {
    val value = mainType.toLowerCase + "/" + subType.toLowerCase
    MimeObjects.get(value).getOrElse(MimeTypes.CustomMimeType(value))
  }
  
}