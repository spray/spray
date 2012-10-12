.. _marshalling:

Marshalling
===========

"Marshalling" is the process of converting a higher-level (object) structure into some kind of lower-level
representation, often a "wire format". Other popular names for it are "Serialization" or "Pickling".

In *spray* "Marshalling" means the conversion of an object of type ``T`` into an ``HttpEntity``, which forms the
"entity body" of an HTTP request or response (depending on whether used on the client or server side).

Marshalling for instances of type ``T`` is performed by a ``Marshaller[T]``, which is defined like this:

.. includecode:: /../spray-httpx/src/main/scala/spray/httpx/marshalling/Marshaller.scala
   :snippet: source-quote

So, a ``Marshaller`` is not a plain function ``T => HttpEntity``, as might be initially expected. Rather it uses the
given MarshallingContext_ to drive the marshalling process from its own side. There are three reasons
why *spray* Marshallers are designed in this way:

- Marshalling on the server-side must support `content negotiation`_, which is easier to implement if the marshaller
  drives the process.
- Marshallers can delay their actions and complete the marshalling process from another thread at another time
  (e.g. when the result of a Future arrives), which is not something that ordinary functions can do. (We could have
  the Marshaller return a Future, but this would add overhead to the majority of cases that do not require delayed
  execution.)
- Marshallers can produce more than one response part, i.e. a stream of response chunks.

.. _MarshallingContext: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/marshalling/MarshallingContext.scala
.. _content negotiation: http://en.wikipedia.org/wiki/Content_negotiation


Default Marshallers
-------------------

*spray-httpx* comes with pre-defined Marshallers for the following types:

.. rst-class:: wide

- BasicMarshallers_

  - ``Array[Byte]``
  - ``Array[Char]``
  - ``String``
  - ``NodeSeq``
  - ``Throwable``
  - ``spray.http.FormData``
  - ``spray.http.StatusCode``
  - ``spray.http.HttpEntity``

- MetaMarshallers_

  - ``Option[T]``
  - ``Either[A, B]``
  - ``Future[T]``
  - ``Stream[T]``

- MultipartMarshallers_

  - ``spray.http.MultipartContent``
  - ``spray.http.MultipartFormData``

.. _BasicMarshallers: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/marshalling/BasicMarshallers.scala
.. _MetaMarshallers: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/marshalling/MetaMarshallers.scala
.. _MultipartMarshallers: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/marshalling/MultipartMarshallers.scala


Implicit Resolution
-------------------

Since the marshalling infrastructure uses a `type class`_ based approach ``Marshaller`` instances for a type ``T`` have
to be available implicitly. The implicits for all the default Marshallers defined by *spray-httpx* are provided
through the companion object of the ``Marshaller`` trait. This means that they are always available and never need to
be explicitly imported. Additionally, you can simply "override" them by bringing your own custom version into local
scope.

.. _type class: http://stackoverflow.com/questions/5408861/what-are-type-classes-in-scala-useful-for


Custom Marshallers
------------------

*spray-httpx* gives you a few convenience tools for constructing Marshallers for your own types.
One is the ``Marshaller.of`` helper, which is defined as such::

    def of[T](marshalTo: ContentType*)
             (f: (T, ContentType, MarshallingContext) => Unit): Marshaller[T]

The default ``StringMarshaller`` for example is defined with it:

.. includecode:: /../spray-httpx/src/main/scala/spray/httpx/marshalling/BasicMarshallers.scala
   :snippet: string-marshaller

As another example, here is a ``Marshaller`` definition for a custom type ``Person``:

.. includecode:: code/docs/MarshallingExamplesSpec.scala
   :snippet: example-1

As can be seen in this example you best define the ``Marshaller`` for ``T`` in the companion object of ``T``.
This way your marshaller is always in-scope, without any `import tax`_.

.. _import tax: http://eed3si9n.com/revisiting-implicits-without-import-tax


Deriving Marshallers
--------------------

Sometimes you can save yourself some work by reusing existing Marshallers for your custom ones.
The idea is to "wrap" an existing ``Marshaller`` with come logic to "re-target" it to your type.

In this regard "wrapping" a ``Marshaller`` can mean one or both of the following two things:

- Transform the input before it reaches the wrapped Marshaller
- Transform the output of the wrapped Marshaller

You can do both, but the existing support infrastructure favors the first over the second.
The ``Marshaller.delegate`` helper allows you to turn a ``Marshaller[B]`` into a ``Marshaller[A]``
by providing a function ``A => B``::

    def delegate[A, B](marshalTo: ContentType*)
                      (f: A => B)
                      (implicit mb: Marshaller[B]): Marshaller[A]

This is used, for example, by the ``NodeSeqMarshaller``, which delegates to the ``StringMarshaller`` like this:

.. includecode:: /../spray-httpx/src/main/scala/spray/httpx/marshalling/BasicMarshallers.scala
   :snippet: nodeseq-marshaller

There is also a second overload of the ``delegate`` helper that takes a function ``(A, ContentType) => B`` rather than
a function ``A => B``. It's helpful if your input conversion requires access to the ``ContentType`` that is
marshalled to.

If you want the second wrapping type, transformation of the output, things are a bit harder (and less efficient),
since Marshallers produce HttpEntities rather than Strings. An ``HttpEntity`` contains the *serialized* result, which is
essentially an ``Array[Byte]`` and a ``ContentType``.
So, for example, prepending a string to the output of the underlying ``Marshaller`` would entail deserializing the bytes
into a string, prepending your prefix and reserializing into a byte array.... not pretty and quite inefficient.
Nevertheless, you can do it. Just produce a custom ``MarshallingContext``, which wraps the original one
with custom logic, and pass it to the inner ``Marshaller``. However, a general solution would also require you to
think about the handling of chunked responses, errors, etc.

Because the second form of wrapping is less attractive there is no real helper infrastructure for it.
We generally do not want to encourage such type of design. (With one exception: Simply overriding the Content-Type of
another ``Marshaller`` can be done efficiently. This is why the ``MarshallingContext`` already comes with a
``withContentTypeOverriding`` copy helper.)
