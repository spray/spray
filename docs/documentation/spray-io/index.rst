.. _spray-io:

spray-io
========

Up to release 1.0/1.1-M7 the *spray-io* module `provided a low-level network I/O layer`_ for directly connecting Akka
actors to asynchronous Java NIO sockets. Since then the *spray* and Akka teams have `joined forces`_ to build upon the
work in *spray* and come up with an extended and improved implementation, which lives directly in Akka as of Akka 2.2.

Over time more and more things that were previously provided by *spray-io* (e.g. the `pipelining infrastructure`_ and
the `SSL/TLS support`_) have found their way, in an improved form, from the *spray* codebase into the Akka codebase, so that
*spray's* own IO module will cease to exist in the near future.

In release 1.2 *spray-io* only contains a few remnants of the earlier infrastructure, which haven't been completely
upgraded to the Akka 2.2 I/O layer yet. So, usually there should be no reason to depend on *spray-io* from your
own applications anymore.

All documentation for the new I/O layer can be found in the `docs to Akka 2.2`_, namely:

- Introduction_
- `I/O Layer Design`_
- `TCP Support`_
- `UDP Support`_
- `Pipeline Infrastructure`_

.. _provided a low-level network I/O layer: /documentation/1.1-M7/spray-io/
.. _pipelining infrastructure: /documentation/1.1-M7/spray-io/pipelining/
.. _SSL/TLS support: /documentation/1.1-M7/spray-io/predefined-stages/#ssltlssupport
.. _docs to Akka 2.2: http://doc.akka.io/docs/akka/2.2.0-RC1/scala.html
.. _joined forces: https://groups.google.com/d/msg/spray-user/9mVRCDdWjn0/kd4CsXowQT8J
.. _in the docs to Akka 2.2: http://doc.akka.io/docs/akka/2.2.0-RC1/scala.html
.. _Introduction: http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io.html
.. _I/O Layer Design: http://doc.akka.io/docs/akka/2.2.0-RC1/dev/io-layer.html#io-layer
.. _TCP Support: http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io-tcp.html
.. _UDP Support: http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io-udp.html
.. _Pipeline Infrastructure: http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io-codec.html