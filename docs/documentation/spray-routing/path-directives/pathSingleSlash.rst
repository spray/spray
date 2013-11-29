.. _-pathSingleSlash-:

pathSingleSlash
===============

Only passes the request to its inner route if the unmatched path of the ``RequestContext``
contains exactly one single slash.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/PathDirectives.scala
   :snippet: pathSingleSlash


Description
-----------

This directive is a simple alias for ``pathPrefix(PathEnd)`` and is mostly used for matching requests to the root URI
(``/``) on an inner-level to discriminate "all path segments matched" from other alternatives (see the example below).


Example
-------

.. includecode:: ../code/docs/directives/PathDirectivesExamplesSpec.scala
   :snippet: pathSingleSlash-