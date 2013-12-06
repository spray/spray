package spray.site

import spray.http.{ StatusCodes, MediaType }
import spray.routing.{ Route, Directives }
import spray.json._
import spray.json.DefaultJsonProtocol._

case class DirectivesGroup(name: String, directives: Seq[String])
object DirectivesGroup {
  implicit val simpleDirectivesList: JsonFormat[Seq[String]] = new JsonFormat[Seq[String]] {
    def write(obj: Seq[String]): JsValue = ???
    def read(json: JsValue): Seq[String] = json match {
      case JsString(str) ⇒ str.split(" ")
    }
  }
  implicit val groupFormat: JsonFormat[DirectivesGroup] =
    jsonFormat(DirectivesGroup.apply _, "group", "entries")(implicitly[JsonFormat[String]], simpleDirectivesList)
}

trait SearchSuggestions { self: Directives ⇒
  import spray.httpx.SprayJsonSupport._

  object SuggestionFormats {
    implicit val openSearchSuggestionsFormat = new RootJsonFormat[Suggestions] {
      def write(obj: Suggestions): JsValue =
        JsArray(
          obj.prefix.toJson,
          obj.results.map(_.result).toJson,
          obj.results.map(_.description).toJson,
          obj.results.map(_.url).toJson)
      def read(json: JsValue) = ???
    }
    implicit val typeAheadSuggestionsFormat = new RootJsonFormat[Suggestions] {
      implicit val suggestionResultFormat = jsonFormat(SuggestionResult, "value", "name", "url", "extra")
      def write(obj: Suggestions): JsValue = obj.results.toJson
      def read(json: JsValue) = ???
    }
  }

  // format: OFF
  def searchRoute(host: String) =
    pathPrefix("search") {
      searchRouteFor(host, "documentation", "spray.io 1.2.0 documentation", "Search and suggest spray.io documentation pages")(suggestDocumentationPage)
    }

  def searchRouteFor(host: String, name: String, shortName: String, description: String)(suggest: String ⇒ Suggestions): Route =
    pathPrefix(name) {
      pathEnd {
        parameter('terms) { terms ⇒
          suggest(terms).results.headOption match {
            case Some(result) ⇒ redirect(result.url, StatusCodes.SeeOther)
            case None         ⇒ reject
          }
        }
      } ~
      path("descriptor") {
        respondWithMediaType(MediaType.custom("application/opensearchdescription+xml")) {
          complete(descriptor(shortName, description, host, name))
        }
      } ~
      path("suggest") {
        parameter('terms) { terms ⇒
          import SuggestionFormats.openSearchSuggestionsFormat
          complete(suggest(terms))
        }
      } ~
      path("typeahead") {
        parameter('terms) { terms =>
          import SuggestionFormats.typeAheadSuggestionsFormat
          complete(suggest(terms))
        }
      }
    }
  // format: ON

  val FullPath = """([^:]+):? (.*)""".r
  def suggestor(data: Seq[ContentNode])(asSuggestion: ContentNode ⇒ SuggestionResult): String ⇒ Suggestions =
    terms ⇒ {
      val (parent, prefix) = terms match {
        case FullPath(parent, prefix) ⇒ (parent, prefix)
        case _                        ⇒ ("", terms)
      }
      val lowerPrefix = prefix.toLowerCase

      def matchParent(node: ContentNode): Boolean = parent == "" || node.parent.name.contains(parent)
      def matchScore(value: String): Int =
        if (value == prefix) 0 // most important: exact matches
        else if (value.toLowerCase == lowerPrefix) 1
        else math.max(1, value.length - prefix.length) + 10

      val suggs =
        data.filter(node ⇒ node.name.toLowerCase.contains(lowerPrefix) && matchParent(node))
          .sortBy(node ⇒ matchScore(node.name))
          .map(asSuggestion)
      Suggestions(prefix, suggs)
    }

  case class Suggestions(prefix: String, results: Seq[SuggestionResult])
  case class SuggestionResult(result: String, description: String, url: String, extra: Map[String, String] = Map.empty)

  def descriptor(shortName: String, description: String, host: String, path: String) = {
    def url(path: String) = s"http://$host/search/$path"
    <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
      <ShortName>{ shortName }</ShortName>
      <Description>{ description }</Description>
      <Image height="16" width="16" type="image/png">http://{ host }/favicon.png</Image>
      <Url type="text/html" method="get" template={ url(s"$path?terms={searchTerms}") }/>
      <Url type="application/x-suggestions+json" rel="suggestions" template={ url(s"$path/suggest?terms={searchTerms}") }/>
      <Url type="application/opensearchdescription+xml" rel="self" template={ url(s"$path/descriptor") }/>
    </OpenSearchDescription>
  }

  val documentationPages = allNodesBelow(Main.root.find("documentation/1.2.0/").get)

  def allNodesBelow(branch: ContentNode): Seq[ContentNode] =
    if (branch.isLeaf) Seq(branch)
    else branch.children.flatMap(allNodesBelow) :+ branch

  def nodeAsSuggestion(node: ContentNode): SuggestionResult = {
    SuggestionResult(node.parent.name + ": " + node.name /* must be unique */ , node.name, "/" + node.uri, Map("parent" -> node.parent.name))
  }
  val suggestDocumentationPage = suggestor(documentationPages)(nodeAsSuggestion)
}
