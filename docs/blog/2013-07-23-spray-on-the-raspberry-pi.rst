:author: matsluni
:tags: scala, spray-can, raspberry pi
:index-paragraphs: 1
:show-post-structure: yes

spray on the Raspberry Pi
=========================

Introduction
------------

As a spray newbie and general computer enthusiast I thought of playing around with spray.io and my Raspberry Pi (RPI) at home.
In this blog post I want to show a small example of how to get started with spray.io on the RPI.

The `Raspberry Pi`_ is a credit-card sized computer with a huge number of use cases. It can be connected to a TV
and equipped with Raspbmc_ (a special XBMC_-Distribution for the RPI) and then be used as a Home Cinema PC (HTPC).
A photo-enthusiast enhanced his camera and built a RPI-based mini-computer into a battery grip and called this `Camera Pi`_.
Just recently I discovered that it is also possible to build a `custom GoogleTV`_ with the RPI.
A very common use case is as a server in your network e.g. file server or web server.

So, as you can see, there are tons
of things you can do with this little computer. However, due to its limited system resources (memory, CPU) it is sometimes
considered too heavy-weight for JVM-based applications. A perfect challenge for me to see how well Scala, Akka and spray
can scale *down* rather than up.

.. _`Raspberry Pi`: http://www.raspberrypi.org
.. _Raspbmc: http://www.raspbmc.com
.. _XBMC: http://xbmc.org
.. _`Camera Pi`: http://www.davidhunt.ie/?p=2641
.. _`custom GoogleTV`: http://blog.donaldderek.com/2013/06/build-your-own-google-tv-using-raspberrypi-nodejs-and-socket-io/
.. _jetty: http://www.eclipse.org/jetty/

JVMs on the Raspberry Pi
------------------------

Because of the ARM_-based architecture of the RPI and the recently added support for the ARM architecture in popular JDKs,
there are some things to consider in choosing the JDK. This sections gives an overview about the possible options.

The easiest way is to use the JDK bundled with your distribution. You can install it via the known package manager
(e.g. apt). If you choose this way you end up with the *default* OpendJDK. This gives you a working JVM, which is a little
bit slower because it doesn't have support for the specific instruction set of the ARM-architecture. The OpenJDK will fall
back to a VM called ZeroVM_. This is an interpreter-only VM which is very portable but runs a little bit slower.

Additionally, there is the official version of the JDK 8 from Oracle, which is currently in early-access status. This version
has dedicated support for the instruction set of the ARM and therefore is faster than the OpenJDK. A requirement for this
JDK is the support of the hardfp_-api in your OS, because Oracle's JDK depends on this. Raspbian_, a Debian-based
distribution is an operating system with support for hardfp-api and therefore Oracle's JDK.

Furthermore, if you want to experiment, you can choose a completely different JDK. A candidate would be e.g. Avian_.

For this demo I will use Raspbian and the Oracle JDK. I explain the steps in the next paragraphs.

First, you need a working Linux-Distribution on your RPI. You can find the Raspian-images on the RPI downloads_-page.
I'll use the *Raspbian “wheezy”* image with support for hardfp-api.

If your Raspbian installation is in place we can move on further to install the JDK. You can download the JDK from here_.
There is an installguide_ which describes how to install the JDK on the RPI. This is basically a tar-file which you can
then simply un-tar to a folder of your choice. For this demo it will be ok to just un-tar it in the home-folder of the
user *pi*::

    tar -xf jdk-8-ea-b36e-linux-arm-hflt-29_nov_2012.tar

This will create the folder ``jdk1.8.0`` in the current dir. Now, you can type ``./jdk1.8.0/bin/java -version`` to check that
Java will run correctly and to see some version information.

.. _ARM: https://en.wikipedia.org/wiki/ARM_architecture
.. _ZeroVM: http://openjdk.java.net/projects/zero/
.. _hardfp: http://www.raspbian.org/RaspbianFAQ#What_do_you_mean_by_.22soft_float_ABI.22_and_.22hard_float_ABI.22.3F
.. _Raspbian: http://www.raspbian.org
.. _Avian: https://github.com/ReadyTalk/avian
.. _downloads: http://www.raspberrypi.org/downloads
.. _here: https://jdk8.java.net/fxarmpreview/index.html
.. _installguide: https://blogs.oracle.com/hinkmond/entry/quickie_guide_getting_hard_float

spray-can on the Raspberry Pi
-----------------------------

So, after our RPI runs a Linux and a JVM, I want to show you how to get started with spray.io on your RPI.

For this I created a `customized version`_ of the spray-template project from Github. You can clone this customized version
with the command::

    git clone https://github.com/matsluni/spray-template.git

Modifications on the spray-template-project
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This customized version is based on the spray-template-project_ (1.2-M8). This version already uses Akka 2.2 including the
new IO-module developed together with the spray.io team and is completely actor-based. At first I give it a telling name:
``spray-can-rpi`` (see build.sbt). Furthermore, the customized version includes the `assembly plugin`_ for sbt_
(see build.sbt). This is necessary because it is not possible to build the project on the RPI itself. Therefore we build
it on our local system and transfer the complete JAR over to our RPI. This packaging is done by the assembly plugin.

There are further changes, which include a slightly modified Akka dispatcher config (see application.conf) to reduce
the amount of threads to start by the akka-runtime. Otherwise Akka would start up to 64 threads which would kill the JVM
of the RPI. Another minor change is to let spray-can listen to all interfaces of the RPI (see Boot.scala). This makes it
possible to reach the demo-application from other hosts in the network including our local system.

Running the modified project on the RPI
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

After you cloned the git repository you can start *sbt* in this scala project. With the assembly-plugin it is very easy to
package the JAR which contains all the dependencies we need for spray.io. From within sbt you can just type ``assembly`` to
start the packaging process. If everything worked out you should have a JAR file in ``./target/scala-2.10/`` called
``spray-can-rpi-assembly-0.1.jar``. This is the JAR file containing spray.io and everything it needs to run. This JAR can
now transferred to the RPI. If you are on a Unix-like system you can copy it with ``scp`` or if you are on Windows you can
use WinSCP_.

Now the time has come to start the spray app on the RPI. This is easy. If you transferred the JAR from your system to the
home folder of the user pi where you also downloaded the JDK you can just enter::

    ~/jdk1.8.0/bin/java -Xss1M -Xms64M -jar spray-can-rpi-assembly-0.1.jar

This is a standard Java JAR start with modifications for the stacksize of 1 MB (``-Xss1M``) and the start heap size of 64 MB (``-Xms64M``).

If everything worked fine you should see something like::

    [INFO] [05/18/2013 08:28:09.287] [on-spray-can-akka.actor.default-dispatcher-3] [akka://on-spray-can/user/IO-HTTP/listener-0] Bound to /0.0.0.0:8080

Now you can open your browser and direct it to the ip-address of your RPI and the correct port (the port is shown in the
log output of spray-can) and you should see the welcome message of spray-routing and spray-can. This shows that spray-can
now runs on the RPI and is happily answering your requests.

.. _`customized version`: https://github.com/matsluni/spray-template
.. _spray-template-project: https://github.com/spray/spray-template
.. _`assembly plugin`: https://github.com/sbt/sbt-assembly
.. _sbt: https://github.com/sbt/sbt
.. _WinSCP: http://winscp.net/eng/docs/lang:de


A little benchmark
------------------

After getting spray-can to work on the RPI I did some benchmarking to get an understanding of how much is possible with
this setup. I ran my tests on a Model B with 256mb ram over LAN on a plain Raspbian installation. The full hardware-spec
of the RPI can be `inspected here`_. For the actual benchmark I used a tool called wrk_ to run some requests against the
RPI. I used it in the following way and with this parameters::

    ./wrk -c 30 -t 20 -d20s http://raspberrypi:8080/

This command will use 30 open connections with 20 threads and runs a 20 second test against the RPI. In this test I got
around 400 requests/sec which is quite nice and shows that spray.io on the RPI is a really useful setup upon which one can
implement real applications.

.. _`inspected here`: http://en.wikipedia.org/wiki/Raspberry_Pi#Specifications
.. _wrk: https://github.com/wg/wrk


Conclusion
----------

The goal of this blog post was to have a JDK-based HTTP-Server running on the RPI. I can say with spray.io this is
possible. The customized version can be seen as a first step to build your own applications running on the RPI.

Furthermore, with this post I wanted to show some more things:

1. How easy it is to deploy spray.io on an embedded-like system like the RPI.
2. Scala can keep the promise to be a scalable language and platform, especially with the results from the little benchmark shown before.
3. Make myself more familiar with spray.io, Scala and the whole ecosystem to be able to build larger applications in the future.

For some feedback or other questions you can reach me via my twitter account `@matsluni`_.

Finally, I want to thank Mathias and Johannes from the spray.io team for this great piece of software, to make the guest
post happen and also the support they gave me during the creation of this post.

.. _`@matsluni`: https://twitter.com/Matsluni