.. _-authorize-:

authorize
=========

Guards access to the inner route with a user-defined check.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/SecurityDirectives.scala
   :snippet: authorize

Description
-----------

The user-defined authorization check can either be supplied as a ``⇒ Boolean`` value which is
calculated just from information out of the lexical scope, or as a function ``RequestContext ⇒ Boolean``
which can also take information from the request itself into account.  If the check returns true the request is passed on
to the inner route unchanged, otherwise an ``AuthorizationFailedRejection`` is created,
triggering a ``403 Forbidden`` response by default (the same as in the case of an ``AuthenticationFailedRejection``).

In a common use-case you would check if a user (e.g. supplied by the :ref:`-authenticate-`
directive) is allowed to access the inner routes, e.g. by checking if the user has the needed
permissions.

Example
-------

.. includecode:: ../code/docs/directives/SecurityDirectivesExamplesSpec.scala
   :snippet: authorize-1
