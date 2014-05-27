.. _-optionalAuthenticate-:

optionalAuthenticate
====================

Authenticates a request by checking credentials supplied in the request and extracts a value
representing the authenticated principal.

Signature
---------

::

    def optionalAuthenticate[T](auth: ⇒ Future[Authentication[T]])(implicit executor: ExecutionContext): Directive1[Option[T]]
    def optionalAuthenticate[T](auth: ContextAuthenticator[T])(implicit executor: ExecutionContext): Directive1[Option[T]]

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

The ``optionalAuthenticate`` directive is similar to the ``authenticate`` directive but always extracts an ``Option``
value instead of rejecting the request if no credentials could be found.