.. _maven-repo:

Maven Repository
================

Currently all *spray* artifacts are hosted in this repository:

  http://repo.spray.cc

However, we'll probably move to the Sonatype repository (and therefore Maven Central) at some point in the future.

If you use SBT_ you'll want to add the following resolver::

  resolvers += "spray repo" at "http://repo.spray.cc"


Artifact Naming
---------------

All *spray* artifacts follow this naming scheme:

:Group ID:    ``cc.spray``
:Artifact ID: Module Name
:Version:     Release Version


So, for expressing a dependency on a *spray* module with SBT_ you'll want to add something like this
to your project settings::

  libraryDependencies += "cc.spray" % "spray-can" % "1.0"

Make sure to replace the artifact name and version number with the one you are targeting! (see :ref:`current-versions`)


.. _SBT: http://www.scala-sbt.org/