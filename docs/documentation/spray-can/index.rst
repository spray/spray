.. _spray-can:

spray-can
=========

The *spray-can* module provides a low-level, low-overhead, high-performance HTTP server and client built on top
of :ref:`spray-io`. Both are fully asynchronous, non-blocking and built 100% in Scala on top of Akka. Since their APIs
are centered around Akka concepts such as Actors and Futures they are very easy to integrate into your Akka-based
applications.

.. toctree::

   dependencies
   installation
   configuration
   http-server
   http-client
   http-dialog
   examples
