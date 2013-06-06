package spray

import akka.testkit.TestProbe

package object testkit {
  implicit def probeWithReply(probe: TestProbe): ProbeWithReply = new ProbeWithReply(probe)
  class ProbeWithReply(probe: TestProbe) {
    def reply(msg: Any) = probe.sender.!(msg)(probe.ref)
  }
}
