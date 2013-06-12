Contributing
============

We value all kinds of contributions from the community, not just actual code. Maybe the easiest and yet very good way
of helping us improve *spray* is to ask questions, voice concerns or propose improvements on the :ref:`Mailing List`.
Or simply tell us about you or your organization using *spray* by sending us a small statement for inclusion on the
:ref:`References` page.

If you do like to contribute actual code in the form of bug fixes, new features or other patches this page gives you
more info on how to do it.


Building *spray*
----------------

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


Issue Tracking
--------------

Currently the *spray* team uses the `Issues Page`_ of the projects `github repository`_ for issue management.
If you find a bug and would like to report it please go there and create an issue.

If you are unsure, whether the problem you've found really is a bug please ask on the :ref:`Mailing List` first.


Patch Policy
------------

Contributions to the project, no matter what kind, are always very welcome.
However, patches can only be accepted from their original author.

Along with any patches, please state that the patch is your original work and
that you license the work to the *spray* project under the :ref:`project's open source license <license>`.


Pull Requests
-------------

If you'd like to submit a code contribution please fork the `github repository`_ and `send us pull request`_
against the ``master`` branch (or the respective release branch, depending on what version your patch is targeting).
Please keep in mind that we might ask you to go through some iterations of discussion and refinements before merging.


.. _SBT: http://www.scala-sbt.org/
.. _issues page: https://github.com/spray/spray/issues
.. _github repository: https://github.com/spray/spray/
.. _send us pull request: https://help.github.com/articles/creating-a-pull-request