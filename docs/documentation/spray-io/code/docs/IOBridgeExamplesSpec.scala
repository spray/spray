package docs

import org.specs2.mutable.Specification


class IOBridgeExamplesSpec extends Specification {

  "example-1" in {
    import akka.actor.ActorSystem
    import spray.io.IOExtension

    val system = ActorSystem()

    val ioBridge = IOExtension(system).ioBridge

    // start applications actors, use ioBridge instance
    // ...
  }

}
