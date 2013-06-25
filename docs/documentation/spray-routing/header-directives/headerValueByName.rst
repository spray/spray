.. _-headerValueByName-:

headerValueByName
=================

Extracts the value of the HTTP request header with the given name.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/HeaderDirectives.scala
   :snippet: headerValueByName

Description
-----------

The name can be given as a ``String`` or as a ``Symbol``. If no header with a matching name is found the request
is rejected with a ``MissingHeaderRejection``. If the header is expected to be missing in some cases or to customize
handling when the header is missing use the :ref:`-optionalHeaderValueByName-` directive instead.

Example
-------

.. includecode:: ../code/docs/directives/HeaderDirectivesExamplesSpec.scala
   :snippet: example-1
