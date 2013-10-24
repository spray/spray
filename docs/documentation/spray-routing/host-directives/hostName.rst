.. _-hostName-:

hostName
========

Extracts the hostname part of the Host header value in the request.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/HostDirectives.scala
   :snippet: hostName


Description
-----------

Extract the hostname part of the ``Host`` request header and expose it as a ``String`` extraction
to its inner route.


Example
-------

.. includecode:: ../code/docs/directives/HostDirectivesExamplesSpec.scala
   :snippet: extract-hostname