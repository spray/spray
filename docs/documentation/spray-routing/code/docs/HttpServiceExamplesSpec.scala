package docs

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import akka.actor.{ActorSystem, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import spray.testkit.Specs2RouteTest
import spray.testkit.Specs2Utils._
import spray.http.StatusCodes

class HttpServiceExamplesSpec extends Specification with Specs2RouteTest {

  //# minimal-example
  import spray.routing.SimpleRoutingApp

  object Main extends App with SimpleRoutingApp {
    implicit val system = ActorSystem("my-system")

    startServer(interface = "localhost", port = 8080) {
      path("hello") {
        get {
          complete {
            <h1>Say hello to spray</h1>
          }
        }
      }
    }
  }
  //#

  trait MyApp {
    import spray.httpx.unmarshalling._
    import spray.httpx.marshalling._
    type Money = Double
    type TransactionResult = String
    case class Order(email: String, amount: Money)
    case class Update(order: Order)
    case class OrderItem(i: Int, os: Option[String], s: String)
    def getOrdersFromDB = ""
    def processOrderRequest(id: Int, f: Order => Unit): Unit = {}
    def myDbActor: ActorRef = null
    implicit val umOrder: Unmarshaller[Order] = null
    implicit val mOrder: Marshaller[Order] = null
    implicit val timeout: Timeout = Duration(1, SECONDS)
  }

  //# longer-example
  import scala.concurrent.duration.Duration
  import spray.routing.HttpService
  import spray.routing.authentication.BasicAuth
  import spray.routing.directives.CachingDirectives._
  import spray.httpx.encoding._

  trait LongerService extends HttpService with MyApp {

    val simpleCache = routeCache(maxCapacity = 1000, timeToIdle = Duration("30 min"))

    val route = {
      path("orders") {
        authenticate(BasicAuth(realm = "admin area")) { user =>
          get {
            cache(simpleCache) {
              encodeResponse(Deflate) {
                complete {
                  // marshal custom object with in-scope marshaller
                  getOrdersFromDB
                }
              }
            }
          } ~
          post {
            (decodeRequest(Gzip) | decodeRequest(NoEncoding)) {
              // unmarshal with in-scope unmarshaller
              entity(as[Order]) { order =>
                // transfer to newly spawned actor
                detachTo(singleRequestServiceActor) {
                  complete {
                    // ... write order to DB
                    "Order received"
                  }
                }
              }
            }
          }
        }
      } ~
      // extract URI path element as Int
      pathPrefix("order" / IntNumber) { orderId =>
        path("") {
          // method tunneling via query param
          (put | parameter('method ! "put")) {
            // form extraction from multipart or www-url-encoded forms
            formFields('email, 'total.as[Money]).as(Order) { order =>
              complete {
                // complete with serialized Future result
                (myDbActor ? Update(order)).mapTo[TransactionResult]
              }
            }
          } ~
          get {
            // JSONP support
            jsonpWithParameter("callback") {
              // use in-scope marshaller to create completer function
              produce(instanceOf[Order]) { complete => ctx =>
                processOrderRequest(orderId, complete)
              }
            }
          }
        } ~
        path("items") {
          get {
            // parameters to case class extraction
            parameters('size.as[Int], 'color ?, 'dangerous ? "no")
                    .as(OrderItem) { orderItem =>
              // ... route using case class instance created from
              // required and optional query parameters
              complete("") // hide
            }
          }
        }
      } ~
      path("documentation") {
        // cache responses to GET requests
        cache(simpleCache) {
          // serve up static content from a JAR resource
          encodeResponse(Gzip) {
            getFromResourceDirectory("docs")
          }
        }
      } ~
      path("oldApi" / Rest) { path =>
        redirect("http://oldapi.example.com/" + path, StatusCodes.MovedPermanently)
      }
    }
  }
  //#

  "longer example" in {
    val service = new LongerService {
      def actorRefFactory = system
    }
    Get("/oldApi/1") ~> service.route ~> check {
      status === StatusCodes.MovedPermanently
    }
  }

  "example-3" in compileOnly {
    import spray.http._
    import HttpMethods._

    class MyHttpService extends Actor {
      def receive = {
        case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
          sender ! HttpResponse(entity = "PONG")
      }
    }
  }

  "example-4" in compileOnly {
    import spray.routing._

    class MyHttpService extends HttpServiceActor {
      def system = 0 // shadow implicit from test, hide
      def receive = runRoute {
        path("ping") {
          get {
            complete("PONG")
          }
        }
      }
    }
  }
}
