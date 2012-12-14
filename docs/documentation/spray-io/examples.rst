Examples
========

The ``/examples/spray-io/`` directory of the *spray* repository
contains a number of example projects for *spray-io*, which are described here.


echo-server
-----------

This example demonstrates how you can create a very simple network server with *spray-io*.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project echo-server" run

   (If this doesn't work for you your SBT runner cannot deal with grouped arguments. In this case you'll have to
   run the commands ``project echo-server`` and ``run`` sequentially "inside" of SBT.)

4. Run ``telnet localhost 23456``, type something and press RETURN

5. Type ``STOP`` to exit