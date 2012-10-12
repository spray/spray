package docs

import org.specs2.mutable.Specification
import akka.util.Timeout
import akka.actor._      // example-1, example-4
import akka.pattern.ask  // example-4
import spray.util._   // example-4


class UtilExamplesSpec extends Specification {
  sequential
                             // example-1, example-4
  val system = ActorSystem() // example-1, example-4

  //# example-1

  val echoActor = system.actorOf {
    Props {
      new Actor {
        def receive = { case msg => sender ! msg.toString }
      }
    }
  }

  val logToConsoleActor = system.actorOf {
    Props {
      new Actor {
        def receive = { case msg => println(msg) }
      }
    }
  }
  //#

  "example-1" in {

    echoActor.tell(42, logToConsoleActor)
    success // hide
  }

  "example-2" in {
    def modReplyActor(receiver: ActorRef) =
      system.actorOf {
        Props {
          new Actor {
            def receive = {
              case msg =>
                receiver ! "The answer is: " + msg
                context.stop(self)
            }
          }
        }
      }

    echoActor.tell(42, modReplyActor(logToConsoleActor))
    success // hide
  }

  "example-3" in {
    import akka.spray.UnregisteredActorRef

    def modReply(receiver: ActorRef) =
      new UnregisteredActorRef(system) {
        def handle(msg: Any)(implicit sender: ActorRef) {
          receiver ! "The answer is: " + msg
        }
      }

    echoActor.tell(42, modReply(logToConsoleActor))
    success // hide
  }

  "example-4" in {
    implicit val timeout = Timeout(1000)

    val echoActor = system.actorOf {
      Props {
        new Actor {
          def receive = { case msg => sender ! msg.toString }
        }
      }
    }

    val mainActor = system.actorOf {
      Props {
        new Actor {
          def receive = {
            case 'run =>
              echoActor.tell(42, Reply.withContext(sender))

            case Reply("42", originalSender: ActorRef) =>
              originalSender ! 'Ok
          }
        }
      }
    }

    mainActor.ask('run).await === 'Ok
  }

  step(system.shutdown())

}
