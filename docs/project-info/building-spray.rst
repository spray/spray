Building *spray*
================

Since *spray* is open-source and hosted on github you can easily build it yourself.

Here is how:

1. Install SBT_ (the master branch is currently built with SBT_ 0.12.3).
2. Check out the *spray* source code from the `github repository`_. Pick the branch corresponding to the version
   you are targeting (check the :ref:`Current Versions` chapter for more info on this).
3. Run ``sbt compile test`` to compile the suite and run all tests.


git branching model
-------------------

The *spray* team follows the "standard" practice of using the ``master`` branch as main integration branch,
with WIP- and feature branches branching of it. The rule is to keep the ``master`` branch always "in good shape",
i.e. having it compile and test cleanly.

Additionally we maintain release branches for older and possibly future releases.


.. _SBT: http://www.scala-sbt.org/
.. _github repository: https://github.com/spray/spray/