.. _Complete Examples:

Complete Examples
=================

The ``/examples/spray-routing/`` directory of the *spray* repository
contains a number of example projects for *spray-routing*, which are described here.

.. _on-spray-can:

on-spray-can
------------

This examples demonstrates how to run *spray-routing* on top of the :ref:`spray-can` :ref:`HttpServer`.
It implements a very simple web-site and shows off various features like streaming, stats support and timeout handling.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project on-spray-can" run

   (If this doesn't work for you your SBT runner cannot deal with grouped arguments. In this case you'll have to
   run the commands ``project on-spray-can`` and ``run`` sequentially "inside" of SBT.)

4. Browse to http://127.0.0.1:8080/

5. Alternatively you can access the service with ``curl``::

    curl -v 127.0.0.1:8080/ping

6. Stop the service with::

    curl -v 127.0.0.1:8080/stop


on-jetty
--------

This examples demonstrates how to run *spray-routing* on top of :ref:`spray-servlet`.
It implements a very simple web-site and shows off various features like streaming, stats support and timeout handling.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project on-jetty" container:start shell

4. Browse to http://127.0.0.1:8080/

5. Alternatively you can access the service with ``curl``::

    curl -v 127.0.0.1:8080/ping

6. Stop the service with::

    container:stop