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


git Branching Model
-------------------

The *spray* team follows the "standard" practice of using the ``master`` branch as main integration branch,
with WIP- and feature branches branching of it. The rule is to keep the ``master`` branch always "in good shape",
i.e. having it compile and test cleanly.

Additionally we maintain release branches for older and possibly future releases.


git Commit Messages
-------------------

We follow the "imperative present tense" style for commit messages (more info here__) and begin each message with
the module name(s) touched by the commit followed by a colon. Additionally, in order to make it easier for us
(and everyone else) to track the effects of changes onto the public API we also explicitly classify every commit into
exactly one of three categories:

__ http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html

.. rst-class:: table table-striped

========= =============================================================================== ======
Category  Description                                                                     Marker
========= =============================================================================== ======
Neutral   Only touches things "under the hood" and has no effect on *spray's* public API. ``=``
Extending Extends the API by adding things. In rare cases this might break code due to    ``+``
          things like identifier shadowing but is generally considered a "safe" change.
Breaking  Changes or removes public API elements. Will definitely break user code         ``!``
          relying on these parts of the API surface.
========= =============================================================================== ======

Apart from the actual Scala interfaces the public API surface covered by these categories also includes configuration
settings (most importantly the ``reference.conf`` files).

The category that a commit belongs to is indicated with a respective marker character that the commit's message must
start with (followed by a space char), e.g. ``= testkit: clean up imports``. Note that *all* commits must carry exactly
one of the markers listed in the table above, with one exception: merge commits that don't introduce any changes
themselves do not have to carry a marker. Instead, they start with "Merge".
Requiring the marker makes sure that the committer has actively thought about the effects of the commit on the public
API surface.

Also, all commits in the "Extending" and especially in the "Breaking" category should contain a dedicated paragraph
(in addition to the summary line) explaining in what way the change breaks the API and why this is necessary/beneficial.
These paragraphes form the basis of release-to-release migration guides.


Issue Tracking
--------------

Currently the *spray* team uses the `Issues Page`_ of the projects `github repository`_ for issue management.
If you find a bug and would like to report it please go there and create an issue.

If you are unsure, whether the problem you've found really is a bug please ask on the :ref:`Mailing List` first.


Contributor License Agreement (CLA)
-----------------------------------

Contributions to the project, no matter what kind, are always very welcome.
However, we can only accept patches if the patch is your original work and you license your work to the *spray* project
under the :ref:`project's open source license <license>`. In order the provide a proper legal foundation for this we
need to ask you to sign `our CLA`_, which is a direct adaptation of the
`Apache Foundation's Individual Contributor License Agreement V2.0`__.

If you have not already done so, please
download_, complete and sign a copy of the CLA and then scan and :ref:`email <Contact>` us a PDF file!
If you prefer you can also snail-mail us the original, please ask for the mailing address.

.. _download: `our CLA`_
.. _our CLA: /spray.io-CLA.pdf
__ http://www.apache.org/licenses/icla.txt


Pull Requests
-------------

If you'd like to submit a code contribution please fork the `github repository`_ and `send us pull request`_
against the ``master`` branch (or the respective release branch, depending on what version your patch is targeting).
Please keep in mind that we might ask you to go through some iterations of discussion and refinements before merging and
that you will need have signed a CLA first!


.. _SBT: http://www.scala-sbt.org/
.. _issues page: https://github.com/spray/spray/issues
.. _github repository: https://github.com/spray/spray/
.. _send us pull request: https://help.github.com/articles/creating-a-pull-request