package cc.spray.http
package parser

trait CommonActions {
  
  def getMimeType(mainType: String, subType: String): MimeType = {
    val value = mainType.toLowerCase + "/" + subType.toLowerCase
    MimeTypes.get(value).getOrElse(MimeTypes.CustomMimeType(value))
  }
  
}