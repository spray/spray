.. _ResponseTransformation:

Response Transformation
=======================

The counterpart to :ref:`RequestBuilding` is the ResponseTransformation__ trait, which is especially useful on the
client-side when you want to transform an incoming HTTP response in a number of loosely coupled steps into some kind of
higher-level result type (see also :ref:`spray-client`).

__ https://github.com/spray/spray/blob/release/1.2/spray-httpx/src/main/scala/spray/httpx/ResponseTransformation.scala

Just like with ``RequestBuilding`` the ``ResponseTransformation`` trait gives you the ``~>`` operator, which allows
you to "append" a transformation function onto an existing function producing an ``HttpResponse``. Thereby it doesn't
matter whether the result is a plain response or a response wrapped in a ``Future``.

For example, if you have a function:

.. includecode:: code/docs/ResponseTransformationExamplesSpec.scala
   :snippet: part-1

and a "response transformer":

.. includecode:: code/docs/ResponseTransformationExamplesSpec.scala
   :snippet: part-2

you can use the ``~>`` operator to combine the two:

.. includecode:: code/docs/ResponseTransformationExamplesSpec.scala
   :snippet: part-3

More generally the ``~>`` operator combines functions in the following ways:

.. rst-class:: table table-striped

==============  ==============  ==============
X               Y               X ~> Y
==============  ==============  ==============
A => B          B => C          A => C
A => Future[B]  B => C          A => Future[C]
A => Future[B]  B => Future[C]  A => Future[C]
==============  ==============  ==============


Predefined Response Transformers
--------------------------------

decode(decoder: Decoder): HttpResponse ⇒ HttpResponse
  Decodes a response using the given Decoder (``Gzip`` or ``Deflate``).

unmarshal[T: Unmarshaller]: HttpResponse ⇒ T
  Unmarshalls the response to a custom type using the in-scope ``Unmarshaller[T]``.

logResponse(...): HttpResponse ⇒ HttpResponse
  Doesn't actually change the response but simply logs it.
