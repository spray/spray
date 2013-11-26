.. _-setCookie-:

setCookie
=========

Adds a header to the response to request the update of the cookie with the given name on the client.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CookieDirectives.scala
   :snippet: setCookie

Description
-----------

Use the :ref:`-deleteCookie-` directive to delete a cookie.


Example
-------

.. includecode:: ../code/docs/directives/CookieDirectivesExamplesSpec.scala
   :snippet: setCookie
