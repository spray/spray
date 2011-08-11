package cc.spray.examples.deft

import cc.spray.connectors.DeftRunner
import org.slf4j.LoggerFactory

object Main extends App with DeftRunner {

  LoggerFactory.getLogger(getClass) // initialize SLF4J early

  boot()
  run()
  shutdown()
}