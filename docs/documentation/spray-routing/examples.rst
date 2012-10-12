.. _Complete Examples:

Complete Examples
=================

The ``/examples/spray-routing/`` directory of the *spray* repository
contains a number of example projects for *spray-routing*, which are described here.

.. _simple-on-spray-can:

simple-on-spray-can
-------------------

This examples demonstrates how to run *spray-routing* on top of the :ref:`spray-can` :ref:`HttpServer`.
It implements a very simple web-site and shows off various features like streaming, stats support and timeout handling.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-on-spray-can" run

4. Browse to http://127.0.0.1:8080/

5. Alternatively you can access the service with ``curl``::

    curl -v 127.0.0.1:8080/ping

6. Stop the service with::

    curl -v 127.0.0.1:8080/stop


simple-on-jetty
---------------

This examples demonstrates how to run *spray-routing* on top of :ref:`spray-servlet`.
It implements a very simple web-site and shows off various features like streaming, stats support and timeout handling.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-on-jetty" container:start shell

4. Browse to http://127.0.0.1:8080/

5. Alternatively you can access the service with ``curl``::

    curl -v 127.0.0.1:8080/ping

6. Stop the service with::

    container:stop