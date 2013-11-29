.. _-anyParams-:

anyParams
=========

The ``anyParams`` directive allows to extract values both from query parameters and form fields.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/AnyParamDirectives.scala
   :snippet: anyParams

Description
-----------

The directives combines the functionality from :ref:`-parameters-` and :ref:`-formFields-` in one directive. To be able
to unmarshal a parameter to a value of a specific type (e.g. with ``as[Int]``) you need to fulfill the requirements
as explained both for :ref:`-parameters-` and :ref:`-formFields-`.

There's a singular version, :ref:`-anyParam-`.

Example
-------

.. includecode:: ../code/docs/directives/AnyParamDirectivesExamplesSpec.scala
   :snippet: example-1

