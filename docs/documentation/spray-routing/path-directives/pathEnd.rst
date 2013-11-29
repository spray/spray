.. _-pathEnd-:

pathEnd
=======

Only passes the request to its inner route if the unmatched path of the ``RequestContext`` is empty, i.e. the request
path has been fully matched by a higher-level :ref:`-path-` or :ref:`-pathPrefix-` directive.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/PathDirectives.scala
   :snippet: pathEnd


Description
-----------

This directive is a simple alias for ``rawPathPrefix(PathEnd)`` and is mostly used on an
inner-level to discriminate "path already fully matched" from other alternatives (see the example below).


Example
-------

.. includecode:: ../code/docs/directives/PathDirectivesExamplesSpec.scala
   :snippet: pathEnd-