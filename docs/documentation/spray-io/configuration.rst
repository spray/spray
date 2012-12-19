Configuration
=============

Just like Akka *spray-io* relies on the `typesafe config`_ library for configuration. As such its JAR contains a
``reference.conf`` file holding the default values of all configuration settings. In your application you typically
provide an ``application.conf``, in which you override Akka and/or *spray* settings according to your needs.

.. note:: Since *spray* uses the same configuration technique as Akka you might want to check out the
   `Akka Documentation on Configuration`_.

.. _typesafe config: https://github.com/typesafehub/config
.. _Akka Documentation on Configuration: http://doc.akka.io/docs/akka/2.0.4/general/configuration.html

This is the ``reference.conf`` of the *spray-io* module:

.. literalinclude:: /../spray-io/src/main/resources/reference.conf
   :language: bash