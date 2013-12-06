.. _-unmatchedPath-:

unmatchedPath
=============

Extracts the unmatched path from the request context.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: unmatchedPath


Description
-----------

The ``unmatchedPath`` directive extracts the remaining path that was not yet matched by any of the :ref:`PathDirectives`
(or any custom ones that change the unmatched path field of the request context). You can use it for building directives
that handle complete suffixes of paths (like the ``getFromDirectory`` directives and similar ones).

Use ``rewriteUnmatchedPath`` to change the value of the unmatched path.


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: unmatchedPath-example
