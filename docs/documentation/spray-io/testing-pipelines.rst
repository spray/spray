.. _testing-pipelines:

Testing Pipelines
=================

Besides the architectural cleanliness a nice side effect of *spray-ios* :ref:`pipelining` architecture is that you can test
individual pipeline stages, or complete stacks of them, easily and without the need to fire up actors.

*spray-io* comes with the PipelineStageTest_ trait, which you can mix into any test specification. It gives you a
small DSL that allows for convenient testing of a pipeline stage (which can also be a combination of sub stages).

As an example you might want to take a look at the `ConnectionTimeoutsSpec`_, which tests the :ref:`ConnectionTimeouts`
pipeline stage.

Overview
--------

The ``PipelineStageTest`` trait contains a "pimp" for ``PipelineStage`` instances, which gives you a ``test`` method
taking a body of test code::

    val stage: PipelineStage = ...
    stage.test {
      ... // test code
    }

The code running "inside" of the ``test`` method has access to a number of helpers allowing for pushing commands and
events through the pipeline stage and inspecting the produced messages.

Process
-------

The most important helper is ``process``:

.. includecode:: /../spray-io/src/main/scala/spray/io/PipelineStageTest.scala
   :snippet: source-quote-process

It allows you to push a number of commands and/or events into the respective ends of the pipeline stage and collect
the commands and events produced by the stage::

    val stage: PipelineStage = ...
    stage.test {
      val result = process(MyCommand, MyEvent)
      ...
    }

The result produced by the ``process`` method is an instance of ``ProcessResult``, which is defined as such:

.. includecode:: /../spray-io/src/main/scala/spray/io/PipelineStageTest.scala
   :snippet: source-quote-result

It contains a "snapshot" of the current state of the internal message collector. If you call ``process`` several times
the collected messages will accumulate. So, this snippet::

    process(MyCommand)
    val result = process(MyEvent)

is equivalent to this one::

    val result = process(MyCommand, MyEvent)

You can clear the message collector with ``clear()``. Also there are two variants of ``process``, which combine it with
a ``clear()``:

.. includecode:: /../spray-io/src/main/scala/spray/io/PipelineStageTest.scala
   :snippet: source-quote-clears


Extractors
----------

Once you have a ``ProcessResult`` instance you could "manually" inspect it and express assertions against its contents
using the constructs of your test framework. For example, using specs2_, you might say something like this::

    val stage: PipelineStage = ...
    stage.test {
      val result = process(MyCommand, MyEvent)
      result.commands(0) === MyFirstExpectedCommand
      result.commands(1) === MySecondExpectedCommand
    }

However, this manual decomposition of the ``ProcessResult`` can become tedious for more complex checks.
Using the simple ``Commands`` extractor that the ``PipelineStageTest`` trait provides the test becomes a bit better to
read::

    val stage: PipelineStage = ...
    stage.test {
      val Commands(first, second) = process(MyCommand, MyEvent)
      first === MyFirstExpectedCommand
      second === MySecondExpectedCommand
    }

There is also an ``Events`` extractor that allows you to pattern match against the collected event messages.


Message Conditioning
--------------------

Writing tests using ``Send`` commands and ``Received`` messages can be a bit inconvenient, since both of them carry
their content in binary form as byte arrays wrapped by a ``java.nio.ByteBuffer``. To simplify test code the
``PipelineStageTest`` therefore automatically converts ``Send`` commands into ``SendString`` commands, which allow
you directly test against String literals::

     val Commands(msg) = process(...)
     msg === SendString("expected content")

The same is done on the event-side to ``Received`` events, which are automatically converted to ``ReceivedString``
events.

Additionally the ``PipelineStageTest`` trait provides helpers to create ``Send`` and ``Received`` commands directly
from strings::

     process(Received("received content"), Send("sent content"))


Messages to and from Actors
---------------------------

Sometimes pipelines stage logic needs to use the ``sender`` reference of an incoming message. In order to simulate the
reception of a message from a specific sender the ``PipelineStageTest`` provides the ``Message`` type:

.. includecode:: /../spray-io/src/main/scala/spray/io/PipelineStageTest.scala
   :snippet: source-quote-message

For example, to feed the pipeline stage with a ``Send`` command sent by ``sender1`` you would say::

    process(Message(Send("sent content"), sender1))

In order to be able to verify that a pipeline stage sends the expected messages to other actors all pipeline stages
should use the ``IOPeer.Tell`` command, rather than sending the message directly. Not only does this allow you to treat
message sending like any other command (and verify it using the techniques explained above), it also allows other
downstream stages in the command pipeline to see the ``Tell`` and potentially modify or react to it.


.. _PipelineStageTest: https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/PipelineStageTest.scala
.. _ConnectionTimeoutsSpec: https://github.com/spray/spray/blob/master/spray-io/src/test/scala/spray/io/ConnectionTimeoutsSpec.scala
.. _specs2: http://specs2.org