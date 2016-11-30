.. _Current Versions:

Current Versions
================

Since *spray* heavily depends on Akka_ its releases are usually closely tied to specific Akka versions.


0.9.0
-----

This version targets Scala 2.9.x and Akka 1.3.x and is therefore not recommended for new projects anymore.
Its documentation doesn't live here but in the (old) `github wiki`_.

1.0.1
-----

This is the latest version targeting Scala 2.9.3 and Akka 2.0.5.
Its sources live in the `release/1.0`_ branch of the *spray* repository.


1.1.4 / 1.2.4 / 1.3.4
---------------------

The current and stable *spray* release is a triple release,
targeting both Scala 2.10 and Scala 2.11 as well as three Akka versions at the same time.

Please choose |1.1|, |1.2| or |1.3| depending on what Scala/Akka version you are targeting:

.. rst-class:: wide

- | *spray* |1.1| is built against Scala 2.10.5 and Akka 2.1.4.
  | Its sources live in the `release/1.1`_ branch of the *spray* repository.

- | *spray* |1.2| is built against Scala 2.10.5 and Akka 2.2.5.
  | Its sources live in the `release/1.2`_ branch of the *spray* repository.
  | (Please note that Akka 2.2.3 or later is *required*, earlier Akka versions will *not* work!)

- | *spray* |1.3| is built against Scala 2.10.5 and Akka 2.3.9 as well as Scala 2.11.6 and Akka 2.3.9.
  | Its sources live in the `release/1.3`_ branch of the *spray* repository.
  | **Note**: Contrary to version |1.1| and |1.2| the |1.3| release is published with **crosspaths enabled**
  | since it targets two Scala versions at the same time!

For information about where to find the *spray* artifacts please check out the :ref:`maven-repo` chapter.

.. |1.1| replace:: **1.1.4**
.. |1.2| replace:: **1.2.4**
.. |1.3| replace:: **1.3.4**


Shapeless Versions
------------------

If you want to use shapeless_ as well as :ref:`spray-routing` in your application you need to select the version of
*spray(-routing)* that was built against the shapeless_ release which you'd like to use.

- For shapeless_ 1.2.4 you should use *spray* |1.1|, |1.2| or |1.3| and the *spray-routing* module.
- shapeless_ 2.0.0 is not supported any more.
- For shapeless_ 2.3.0 you should use *spray* **1.3.4** (Scala 2.10 or Scala 2.11) and
  the *spray-routing-shapeless23* module instead of *spray-routing*.

.. _shapeless: https://github.com/milessabin/shapeless


Nightly Builds
--------------

If you'd like to have access to the most recent changes and additions without having to build *spray* yourself you can
rely on the nightly builds, which we are currently publishing for the `release/1.0`_, `release/1.1`_, `release/1.2`_ and
`release/1.3`_ branches of the *spray* repository. Every day shortly past midnight UTC a new build is made available
unless the respective branch has not seen any new commits since the last build.

In order to help you identify the exact commit from which a build is cut every artifact directory includes a
``commit-<version>.html`` file containing the commit hash with a link to the commit on github.

Nightly builds are available from the http://nightlies.spray.io repository.

.. _akka: http://akka.io
.. _github wiki: https://github.com/spray/spray/wiki
.. _master: https://github.com/spray/spray
.. _release/1.0: https://github.com/spray/spray/tree/release/1.0
.. _release/1.1: https://github.com/spray/spray/tree/release/1.1
.. _release/1.2: https://github.com/spray/spray/tree/release/1.2
.. _release/1.3: https://github.com/spray/spray/tree/release/1.3
.. _release/1.3_2.11: https://github.com/spray/spray/tree/release/1.3_2.11
