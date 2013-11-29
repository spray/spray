.. _FutureDirectives:

FuturesDirectives
=================

Future directives can be used to run inner routes once the provided ``Future[T]`` has been completed.

.. toctree::
   :maxdepth: 1

   onComplete
   onSuccess
   onFailure

All future directives take a by-name parameter so that the parameter is not evaluated at route building time
but only when the request comes in.
