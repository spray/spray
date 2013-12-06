package spray.site

import spray.http.{ StatusCodes, MediaType }
import spray.routing.{ Route, Directives }
import org.parboiled.common.FileUtils
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.annotation.tailrec

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

  implicit def suggestionsFormat = new RootJsonFormat[Suggestions] {
    def write(obj: Suggestions): JsValue =
      JsArray(
        obj.prefix.toJson,
        obj.results.map(_.result).toJson,
        obj.results.map(_.description).toJson,
        obj.results.map(_.url).toJson)
    def read(json: JsValue): SearchSuggestions.this.type#Suggestions = ???
  }

  // format: OFF
  def searchRoute(host: String) =
    pathPrefix("search") {
      searchRouteFor(host, "directives", "spray.io 1.2.0 directive", "Search and suggest spray.io directives")(suggestDirectives) ~
      searchRouteFor(host, "documentation", "spray.io 1.2.0 documentation", "Search and suggest spray.io documentation pages")(suggestDocumentationPage)
    }

  def searchRouteFor(host: String, name: String, shortName: String, description: String)(suggest: String ⇒ Suggestions): Route =
    pathPrefix(name) {
      pathEnd {
        parameter('terms) { terms ⇒
          suggest(terms).results.headOption match {
            case Some(result) ⇒ redirect(result.url, StatusCodes.SeeOther)
            case None         ⇒ complete(StatusCodes.NotFound, s"$name '$terms' not found")
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
          complete(suggest(terms))
        }
      }
    }
  // format: ON

  def suggestor[T](data: Seq[T])(searchBy: T ⇒ String, asSuggestion: T ⇒ SuggestionResult): String ⇒ Suggestions =
    prefix ⇒ {
      val lowerPrefix = prefix.toLowerCase

      def matchScore(value: String): Int =
        if (value == prefix) 0 // most important: exact matches
        else if (value.toLowerCase == lowerPrefix) 1
        else math.max(1, value.length - prefix.length) + 10

      val suggs =
        data.filter(d ⇒ searchBy(d).toLowerCase.contains(lowerPrefix))
          .sortBy(d ⇒ matchScore(searchBy(d)))
          .map(asSuggestion)
      Suggestions(prefix, suggs)
    }

  case class Suggestions(prefix: String, results: Seq[SuggestionResult])
  case class SuggestionResult(result: String, description: String, url: String)

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

  case class Directive(name: String, group: String, url: String) {
    def asSuggestion = SuggestionResult(name, s"$name of $group directives", url)
  }

  val directives = {
    val content = FileUtils.readAllTextFromResource("theme/js/directives-map.js")
    val json = content.dropWhile(_ != '=').drop(2).dropRight(5) + "]"

    for {
      group ← JsonParser(json).convertTo[Seq[DirectivesGroup]]
      directive ← group.directives
    } yield Directive(directive, group.name, s"/documentation/1.2.0/spray-routing/${group.name}-directives/$directive/")
  }

  val documentationPages: Seq[ContentNode] = {
    val root = Main.root.find("documentation/1.2.0/").get
    def rec(branch: ContentNode): Seq[ContentNode] =
      if (branch.isLeaf) Seq(branch)
      else branch.children.flatMap(rec) :+ branch

    rec(root)
  }

  val suggestDirectives = suggestor(directives)(_.name, _.asSuggestion)
  val suggestDocumentationPage = suggestor(documentationPages)(_.name, cn ⇒ SuggestionResult(cn.name, cn.title, "/" + cn.uri))
}
