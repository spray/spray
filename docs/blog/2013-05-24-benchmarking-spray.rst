:author: Mathias
:tags: benchmarking
:index-paragraphs: 1
:show-post-structure: yes
:scripts: //d3js.org/d3.v3.min.js /js/jquery.powertip.min.js /js/2013-05-24-benchmarking-spray.js
:styles: /css/2013-05-24-benchmarking-spray.css

Benchmarking spray
==================

A few days ago the folks at techempower_ published `round 5`_ of their well-received current series of web framework
benchmarks, the first one in which *spray* participates. The techempower benchmark consist of a number of different
test scenarios exercising various parts of a web framework/stack, only one of which we have supplied a *spray*-based
implementation for: the "JSON serialization" test. The other parts of this benchmark target framework layers (like
database access), which *spray* intentionally doesn't provide.

Here are the published results of the JSON test of `round 5`_ presented in an alternative visualization (but showing
the exact same data):

.. raw:: html

   <div id="svg-container">
    <ul id="svg-legend">
      <li><input id="actual" type="radio" name="what" checked="checked"/>Avg. requests/sec as reported by wrk</li>
      <li><input id="projected" type="radio" name="what"/>Avg. requests/sec projected from avg. latency</li>
      <li class="jvm"><span>&#11044;</span>JVM-based</li>
      <li class="no-jvm"><span>&#11044;</span>Not JVM-based</li>
    </ul>
    <svg></svg>
   </div>

The test was run between two identical machines connected via a GB-Ethernet link, a client machine generating HTTP
requests with wrk_ as the load generator, and a server machine running the respective "benchmarkee". In order to
provide an indication of how performance varies with the underlying hardware platform all tests are run twice,
once between two EC2 "m1.large" instances and once between two dedicated i7-2600K workstations.

.. _techempower: http://www.techempower.com/
.. _round 5: http://www.techempower.com/blog/2013/05/17/frameworks-round-5/
.. _wrk: https://github.com/wg/wrk


Analysis
--------

In the graph above we compare the performance results on dedicated hardware with the ones on the EC2 machines. We would
expect a strong correlation between the two, with most data points assembled around the trendline. The "bechmarkees" that
are far off the trendline either don't scale up or down as well as the "pack" or suffer from some configuration issue on
their "weak" side (e.g. ``cpoll_cppsp`` and ``onion`` on i7 or ``gemini``/``servlet`` and ``spark`` on the EC2). Either
way, some investigation as to the cause of the problem might be advised.

In addition to plotting the average requests/sec numbers reported by wrk_ at the end of a 30 second run we have included
an alternative projection of the request count, based on the average request latencies reported by wrk_ (e.g. 1 ms avg.
latency across 64 connections should result in about 64K avg. req/s). Ideally these projected results should roughly
match the actually reported ones (bar any rounding issues).

However, as you can see in the chart the two results differ substantially for some benchmarkees. To us this is an
indication that something was not quite right during the respective test run. Maybe the client running wrk_ experienced
some other load which affected its ability to either generate requests or measure latency properly. Or we are seeing
the results of wrk's somewhat "unorthodox" request latency sampling implementation. Either way, our confidence regarding
the validity of the avg. request counts and the latency data would be higher if the two results were more closely
aligned.


Take-Aways
----------

The special value of this benchmark stems from the sheer number of different frameworks/libraries/toolsets that the
techempower team has managed to include. Round 5 provides results for a very heterogeneous group of close to 70 (!)
benchmarkees written in 17 different languages.
As such it gives a good indication of the rough performance characteristics that can be expected from the different
solutions. For example, would you have expected a Ruby on Rails application to run about 10-20 times slower than a
good JVM-based alternative? Most people would have assumed a performance difference but the actual magnitude thereof
might come as a surprise and is certainly interesting, not only for someone currently facing a technology decision.

For us as authors of an HTTP stack we look to such benchmarks from a slightly different angle. The main question for us
is: How does our solution perform compared to alternatives *on the same platform*? What can we learn from them? Where
do we still have potential for optimization that we appear to have left on the table? What effect on performance do the
various architecture decisions have that one has to make when writing a library like *spray*?

As you can see from the graph above we can be quite satisfied with *spray's* performance in this particular benchmark.
It outperforms all other JVM-based HTTP stacks on the EC2 and, when looking at throughput projected from the
latency data, even on dedicated hardware.

This shows us that our work on optimizing spray's HTTP implementation is paying off. The version used in this benchmark
is a recent *spray* 1.1 nightly build, which includes most (but not all) performance optimizations planned for the
coming 1.0/1.1/1.2 triple release (1.0 for Akka 2.0, 1.1 for Akka 2.1 and 1.2 for Akka 2.2).

But, does this benchmark prove that *spray* is the fastest HTTP stack on the JVM?

Unfortunately it doesn't. This one test exercises way to small a percentage of all the logic of the various HTTP
implementations in order to be able to properly rank them. It gives an indication, but hardly more.

What's missing?


Benchmarking Wish-List
----------------------

Let's look more closely at what the "JSON serialization test" of the techempower benchmark actually exercises.
The client creates between 8 and 256 long-lived concurrent TCP connections to the server and fires as many test requests
as possible across these connections. Each request hits the server's NIC, bubbles up through the Linux kernel's network
stack, gets picked up by the benchmarkees IO abstraction and is passed on to the HTTP layer (where it is parsed and
maybe routed) before actually being handled by the "application logic". In the case of this benchmark the application
merely creates a small JSON object, puts it into an HTTP response and sends it back down the stack, where it passes all
layers again in the opposite direction.

As such this benchmark tests how well the benchmarkee:

- interacts with the kernel with regard to "pulling out" the raw data arriving at a socket
- manages internal communication between its inner layers (e.g. IO <-> HTTP <-> Application)
- parses HTTP requests and renders HTTP responses
- serializes small JSON objects

It does all this using small requests with a fixed set of HTTP headers over a rather small number of long-lived
connections. Also, it does it all at once without giving us a clue as to the potential strengths and weaknesses of the
individual parts of the stack.

If we wanted to learn something deeper about how *spray* performs compared to its JVM-based competitors and where its
strengths and weaknesses lie we'd have to setup a whole range of benchmarks that measure:

- | raw IO performance:
  | 1 to say 50K long-lived concurrent connections, minimal request and response sizes
- | connection setup overhead:
  | varying number of per-request connections, minimal request and response sizes
- | HTTP request parser performance:
  | varying number of request headers and header value sizes, varying entity sizes
- | HTTP response renderer performance:
  | varying number of response headers and header value sizes, varying entity sizes
- | HTTP chunking performance:
  | chunked requests and responses with varying number and size of message chunks
- | HTTP pipelining performance:
  | varying number of request batch sizes
- | SSL performance:
  | 1 to say 50K long-lived connections, minimal request and response sizes
- | Websocket performance
- | System- and JVM-level metrics (CPU utilization, GC-Activity, etc.)

If we had a benchmark suite producing numbers like these we'd feel much more comfortable in establishing a proper
performance-based ranking of *spray* and its alternatives. And wouldn't it be great if there was something like a
"continuous benchmarking" infrastructure, that would automatically produce these benchmark results upon a simple
``git push`` into its repository?

Oh well... I guess our ever-growing todo-list just received one more item marked *important*... :)

| Cheers,
| Mathias