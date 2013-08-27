.. _-schemeName-:

schemeName
==========

Extracts the value of the request Uri scheme.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/SchemeDirectives.scala
   :snippet: schemeName

Description
-----------

The ``schemeName`` directive can be used to determine the Uri scheme (i.e. "http", "https", etc.)
for an incoming request.

For rejecting a request if it doesn't match a specified scheme name, see the :ref:`-scheme-` directive.

Example
-------

.. includecode:: ../code/docs/directives/SchemeDirectivesExamplesSpec.scala
   :snippet: example-1
