Twirl Support
=============

Twirl_ complements *spray* with templating support.

The TwirlSupport_ trait provides the tiny glue layer required for being able to use twirl templates directly in
:ref:`spray-routing` routes and :ref:`request building <RequestBuilding>`.

Simply mix in the ``TwirlSupport`` trait or ``import spray.httpx.TwirlSupport._``.

.. note:: Since *spray-httpx* only comes with an optional dependency on twirl_ you still have to add it to your
   project yourself. Check the twirl_ documentation for information on how to do this.

.. admonition:: Side Note

   This site, for example, makes use of twirl-templates and TwirlSupport for serving all of its pages_.

.. _twirl: https://github.com/spray/twirl
.. _TwirlSupport: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/TwirlSupport.scala
.. _pages: https://github.com/spray/spray/tree/master/site/src/main/twirl