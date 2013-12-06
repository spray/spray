.. _-rewriteUnmatchedPath-:

rewriteUnmatchedPath
====================

Transforms the unmatchedPath field of the request context for inner routes.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: rewriteUnmatchedPath


Description
-----------

The ``rewriteUnmatchedPath`` directive is used as a building block for writing :ref:`Custom Directives`. You can use it
for implementing custom path matching directives.

Use ``unmatchedPath`` for extracting the current value of the unmatched path.


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: rewriteUnmatchedPath-example
