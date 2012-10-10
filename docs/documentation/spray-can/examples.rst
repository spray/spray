Examples
========

The ``/examples/spray-can/`` directory of the *spray* repository
contains a number of example projects for *spray-can*, which are described here.


.. _simple-http-client:

simple-http-client
------------------

This example demonstrates how you can use the :ref:`HttpClient` and :ref:`HttpDialog` to perform simple,
low-level HTTP client logic.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-http-client" run

4. Type either **1**, **2** or **3** and press **RETURN**


.. _simple-http-server:

simple-http-server
------------------

This examples implements a very simple web-site built with the *spray-can* :ref:`HttpServer`.
It shows off various features like streaming, stats support and timeout handling.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-http-server" run

4. Browse to http://127.0.0.1:8080/

5. Alternatively you can access the service with ``curl``::

    curl -v 127.0.0.1:8080/ping

6. Stop the service with::

    curl -v 127.0.0.1:8080/stop