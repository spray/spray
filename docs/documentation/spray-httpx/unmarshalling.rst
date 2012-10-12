.. _unmarshalling:

Unmarshalling
=============

"Unmarshalling" is the process of converting some kind of a lower-level representation, often a "wire format", into a
higher-level (object) structure. Other popular names for it are "Deserialization" or "Unpickling".

In *spray* "Unmarshalling" means the conversion of an ``HttpEntity``, the model class for the "entity body" of an HTTP
request or response (depending on whether used on the client or server side), into an object of type ``T``.

Unmarshalling for instances of type ``T`` is performed by an ``Unmarshaller[T]``, which is defined like this::

    type Unmarshaller[T] = Deserializer[HttpEntity, T]
    trait Deserializer[A, B] extends (A => Deserialized[B])
    type Deserialized[T] = Either[DeserializationError, T]

So, an ``Unmarshaller`` is basically a function ``HttpEntity => Either[DeserializationError, T]``.
When compared to their counterpart, :ref:`Marshallers <marshalling>`, Unmarshallers are somewhat simpler, since they
are straight functions and do not have to deal with chunk streams (which are currently not supported in unmarshalling)
or delayed execution.)


Default Unmarshallers
---------------------

*spray-httpx* comes with pre-defined Unmarshallers for the following types:

- ``Array[Byte]``
- ``Array[Char]``
- ``String``
- ``NodeSeq``
- ``Option[T]``
- ``spray.http.FormData``
- ``spray.http.HttpForm``
- ``spray.http.MultipartContent``
- ``spray.http.MultipartFormData``

The relevant sources are:

- Deserializer_
- BasicUnmarshallers_
- MetaUnmarshallers_
- MultipartUnmarshallers_

.. _Deserializer: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/unmarshalling/Deserializer.scala
.. _BasicUnmarshallers: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/unmarshalling/BasicUnmarshallers.scala
.. _MetaUnmarshallers: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/unmarshalling/MetaUnmarshallers.scala
.. _MultipartUnmarshallers: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/unmarshalling/MultipartUnmarshallers.scala


Implicit Resolution
-------------------

Since the unmarshalling infrastructure uses a `type class`_ based approach ``Unmarshaller`` instances for a type ``T``
have to be available implicitly. The implicits for all the default Unmarshallers defined by *spray-httpx* are provided
through the companion object of the ``Deserializer`` trait (since ``Unmarshaller[T]`` is just an alias for a
``Deserializer[HttpEntity, T]``). This means that they are always available and never need to be explicitly imported.
Additionally, you can simply "override" them by bringing your own custom version into local scope.

.. _type class: http://stackoverflow.com/questions/5408861/what-are-type-classes-in-scala-useful-for


Custom Unmarshallers
--------------------

*spray-httpx* gives you a few convenience tools for constructing Unmarshallers for your own types.
One is the ``Unmarshaller.apply`` helper, which is defined as such::

    def apply[T](unmarshalFrom: ContentTypeRange*)
                (f: PartialFunction[HttpEntity, T]): Unmarshaller[T]

The default ``NodeSeqMarshaller`` for example is defined with it:

.. includecode:: /../spray-httpx/src/main/scala/spray/httpx/unmarshalling/BasicUnmarshallers.scala
   :snippet: nodeseq-unmarshaller

As another example, here is an ``Unmarshaller`` definition for a custom type ``Person``:

.. includecode:: code/docs/UnmarshallingExamplesSpec.scala
   :snippet: example-1

As can be seen in this example you best define the ``Unmarshaller`` for ``T`` in the companion object of ``T``.
This way your unmarshaller is always in-scope, without any `import tax`_.

.. _import tax: http://eed3si9n.com/revisiting-implicits-without-import-tax


Deriving Unmarshallers
----------------------

Unmarshaller.delegate
~~~~~~~~~~~~~~~~~~~~~

Sometimes you can save yourself some work by reusing existing Marshallers for your custom ones.
The idea is to "wrap" an existing ``Unmarshaller`` with come logic to "re-target" it to your type.

In this regard "wrapping" a ``Unmarshaller`` can mean one or both of the following two things:

- Transform the input ``HttpEntity`` before it reaches the wrapped Unmarshaller
- Transform the output of the wrapped Unmarshaller

You can do both, but the existing support infrastructure favors the latter over the former.
The ``Unmarshaller.delegate`` helper allows you to turn an ``Unmarshaller[A]`` into an ``Unmarshaller[B]``
by providing a function ``A => B``::

    def delegate[A, B](unmarshalFrom: ContentTypeRange*)
                      (f: A => B)
                      (implicit mb: Unmarshaller[A]): Unmarshaller[B]

For example, by using ``Unmarshaller.delegate`` the ``Unmarshaller[Person]`` from the example above could be simplified
to this:

.. includecode:: code/docs/UnmarshallingExamplesSpec.scala
   :snippet: example-2

Unmarshaller.forNonEmpty
~~~~~~~~~~~~~~~~~~~~~~~~

In addition to ``Unmarshaller.delegate`` there is also another "deriving Unmarshaller builder" called
``Unmarshaller.forNonEmpty``. It "modifies" an existing Unmarshaller to not accept empty entities.

For example, the default ``NodeSeqMarshaller`` (see above) accepts empty entities as a valid representation of
``NodeSeq.Empty``. It might be, however, that in your application context empty entities are not allowed.
In order to achieve this, instead of "overriding" the existing ``NodeSeqMarshaller`` with an all-custom
re-implementation you could be doing this:

.. includecode:: code/docs/UnmarshallingExamplesSpec.scala
   :snippet: example-3
