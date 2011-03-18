package cc.spray.marshalling

import cc.spray.http.{RawContent, ContentType}

case class Marshalling(contentTypes: List[ContentType], converter: ContentType => RawContent)