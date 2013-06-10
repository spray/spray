Examples
========

The `/examples/spray-can/`__ directory of the *spray* repository
contains a number of example projects for *spray-can*, which are described here.

__ https://github.com/spray/spray/tree/release/1.0/examples/spray-can

.. _simple-http-client:


simple-http-client
------------------

This example demonstrates how you can use the three different client-side API levels for performing a simple
request/response cycle.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-http-client" run

   (If this doesn't work for you your SBT runner cannot deal with grouped arguments. In this case you'll have to
   run the commands ``project simple-http-client`` and ``run`` sequentially "inside" of SBT.)


.. _simple-http-server:


simple-http-server
------------------

This examples implements a very simple web-site built with the *spray-can* :ref:`HTTP Server`.
It shows off various features like streaming, stats support and timeout handling.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-http-server" run

   (If this doesn't work for you your SBT runner cannot deal with grouped arguments. In this case you'll have to
   run the commands ``project simple-http-server`` and ``run`` sequentially "inside" of SBT.)

4. Browse to http://127.0.0.1:8080/

5. Alternatively you can access the service with ``curl``::

    curl -v 127.0.0.1:8080/ping

6. Stop the service with::

    curl -v 127.0.0.1:8080/stop


server-benchmark
----------------

This example implements a very simple "ping/pong" server for benchmarking purposes, that mirrors the
"JSON serialization" test setup from the `techempower benchmark`_.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project server-benchmark" run

   (If this doesn't work for you your SBT runner cannot deal with grouped arguments. In this case you'll have to
   run the commands ``project server-benchmark`` and ``run`` sequentially "inside" of SBT.)

4. Use a load-generation tool like ab_, weighttp_, wrk_ or the like to fire test requests, e.g.::

    wrk -t4 -c100 -d10 http://127.0.0.1:8080/ping

If you start the server with ``re-start`` rather than ``run`` it will run in a forked JVM that has ``-verbose:gc`` and
``-XX:+PrintCompilation`` flags set, so you can see how often GC is performed and whether the JIT compiler is "done"
with compiling all the hot spots.

.. _techempower benchmark: /blog/2013-05-24-benchmarking-spray/
.. _ab: http://httpd.apache.org/docs/2.2/programs/ab.html
.. _weighttp: http://redmine.lighttpd.net/projects/weighttp/wiki
.. _wrk: https://github.com/wg/wrk