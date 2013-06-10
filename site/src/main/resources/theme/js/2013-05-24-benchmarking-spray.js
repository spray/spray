$(function() {

  // add Array.map
  if (!Array.prototype.map) {
    Array.prototype.map = function(fun) {
      var len = this.length;
      if (typeof fun != "function") throw new TypeError();
      var res = new Array(len);
      var thisp = arguments[1];
      for (var i = 0; i < len; i++) if (i in this) res[i] = fun.call(thisp, this[i], i, this);
      return res;
    }
  }

  function simpleLinearRegression(o) {
    var n = o.length, sum_x = 0, sum_y = 0, sum_xy = 0, sum_xx = 0, sum_yy = 0;
    for (var i = 0; i < n; i++) {
      var x = o[i].x, y = o[i].y;
      sum_x += x;
      sum_y += y;
      sum_xy += x * y;
      sum_xx += x * x;
      sum_yy += y * y;
    }
    var slope = (n * sum_xy - sum_x * sum_y) / (n * sum_xx - sum_x * sum_x);
    var intercept = (sum_y - slope * sum_x) / n;
    return function(x) { return intercept + slope * x }
  }

  var dataset = [
    { "name": "bottle"              , "jvm": 0, "actual": { "ec2": { "rps":   7394, "projected":   9078, "conc":  64, "lat": 0.00705 }, "ded": { "rps":  45963, "projected":  59813, "conc": 256, "lat":  0.00428 } } },
    { "name": "cake"                , "jvm": 0, "actual": { "ec2": { "rps":    272, "projected":    262, "conc":   8, "lat": 0.03043 }, "ded": { "rps":   1753, "projected":   1701, "conc":  32, "lat":  0.01881 } } },
    { "name": "codeigniter"         , "jvm": 0, "actual": { "ec2": { "rps":    594, "projected":    527, "conc":  64, "lat": 0.12132 }, "ded": { "rps":   4002, "projected":   4155, "conc":  64, "lat":   0.0154 } } },
    { "name": "compojure"           , "jvm": 1, "actual": { "ec2": { "rps":  11481, "projected":  11111, "conc": 128, "lat": 0.01152 }, "ded": { "rps":  96876, "projected":  91822, "conc":  64, "lat": 0.000697 } } },
    { "name": "cowboy"              , "jvm": 0, "actual": { "ec2": { "rps":  10247, "projected":   9384, "conc":  64, "lat": 0.00682 }, "ded": { "rps":  71594, "projected":  63366, "conc":  64, "lat":  0.00101 } } },
    { "name": "cpoll_cppsp"         , "jvm": 0, "actual": { "ec2": { "rps":  49899, "projected":  40699, "conc": 256, "lat": 0.00629 }, "ded": { "rps": 194301, "projected": 199376, "conc": 128, "lat": 0.000642 } } },
    { "name": "dancer"              , "jvm": 0, "actual": { "ec2": { "rps":    954, "projected":    962, "conc": 256, "lat": 0.26598 }, "ded": { "rps":   5740, "projected":   5770, "conc":  64, "lat":  0.01109 } } },
    { "name": "django"              , "jvm": 0, "actual": { "ec2": { "rps":   1449, "projected":   1641, "conc":  16, "lat": 0.00975 }, "ded": { "rps":   8567, "projected":   8040, "conc":  16, "lat":  0.00199 } } },
    { "name": "django-stripped"     , "jvm": 0, "actual": { "ec2": { "rps":   3782, "projected":   2650, "conc": 256, "lat": 0.09658 }, "ded": { "rps":  22016, "projected":  25754, "conc": 128, "lat":  0.00497 } } },
    { "name": "dropwizard"          , "jvm": 1, "actual": { "ec2": { "rps":  11037, "projected":  10256, "conc":   8, "lat": 0.00078 }, "ded": { "rps":  96540, "projected":  65306, "conc":  64, "lat":  0.00098 } } },
    { "name": "elli"                , "jvm": 0, "actual": { "ec2": { "rps":  17294, "projected":  16612, "conc": 256, "lat": 0.01541 }, "ded": { "rps": 150633, "projected": 155717, "conc":  64, "lat": 0.000411 } } },
    { "name": "express"             , "jvm": 0, "actual": { "ec2": { "rps":   7000, "projected":   6513, "conc": 256, "lat":  0.0393 }, "ded": { "rps":  42904, "projected":  32820, "conc":  64, "lat":  0.00195 } } },
    { "name": "finagle"             , "jvm": 1, "actual": { "ec2": { "rps":  22925, "projected":  25073, "conc": 256, "lat": 0.01021 }, "ded": { "rps": 146882, "projected": 152380, "conc": 256, "lat":  0.00168 } } },
    { "name": "flask"               , "jvm": 0, "actual": { "ec2": { "rps":   3208, "projected":   2088, "conc":  32, "lat": 0.01532 }, "ded": { "rps":  17959, "projected":  15311, "conc":  32, "lat":  0.00209 } } },
    { "name": "flask-pypy"          , "jvm": 0, "actual": { "ec2": { "rps":   4510, "projected":   4519, "conc":  16, "lat": 0.00354 }, "ded": { "rps":  22841, "projected":  21476, "conc":  32, "lat":  0.00149 } } },
    { "name": "fuel"                , "jvm": 0, "actual": { "ec2": { "rps":    319, "projected":    202, "conc":   8, "lat": 0.03947 }, "ded": { "rps":   1988, "projected":   1886, "conc":  32, "lat":  0.01696 } } },
    { "name": "gemini"              , "jvm": 1, "actual": { "ec2": { "rps":  23550, "projected":  21548, "conc": 128, "lat": 0.00594 }, "ded": { "rps": 204821, "projected": 199066, "conc": 128, "lat": 0.000643 } } },
    { "name": "go"                  , "jvm": 0, "actual": { "ec2": { "rps":  23560, "projected":  25498, "conc": 128, "lat": 0.00502 }, "ded": { "rps": 174996, "projected": 179020, "conc": 256, "lat":  0.00143 } } },
    { "name": "grails"              , "jvm": 1, "actual": { "ec2": { "rps":   4039, "projected":   4018, "conc": 256, "lat":  0.0637 }, "ded": { "rps":  30051, "projected":  29024, "conc": 128, "lat":  0.00441 } } },
    { "name": "grizzly-jersey"      , "jvm": 1, "actual": { "ec2": { "rps":  14321, "projected":  14206, "conc": 256, "lat": 0.01802 }, "ded": { "rps": 115544, "projected": 101185, "conc": 256, "lat":  0.00253 } } },
    { "name": "http-kit"            , "jvm": 1, "actual": { "ec2": { "rps":  21380, "projected":  21440, "conc": 256, "lat": 0.01194 }, "ded": { "rps": 105958, "projected": 106666, "conc": 256, "lat":   0.0024 } } },
    { "name": "kelp"                , "jvm": 0, "actual": { "ec2": { "rps":   1740, "projected":   1742, "conc": 256, "lat": 0.14695 }, "ded": { "rps":  12444, "projected":  11710, "conc": 256, "lat":  0.02186 } } },
    { "name": "kohana"              , "jvm": 0, "actual": { "ec2": { "rps":    355, "projected":    310, "conc":  16, "lat":  0.0516 }, "ded": { "rps":   2090, "projected":   2143, "conc":  32, "lat":  0.01493 } } },
    { "name": "laravel"             , "jvm": 0, "actual": { "ec2": { "rps":    362, "projected":    291, "conc":  16, "lat":  0.0549 }, "ded": { "rps":   2139, "projected":   2122, "conc":  16, "lat":  0.00754 } } },
    { "name": "lift-stateless"      , "jvm": 1, "actual": { "ec2": { "rps":   5067, "projected":   5068, "conc": 256, "lat": 0.05051 }, "ded": { "rps":  37891, "projected":  37317, "conc": 256, "lat":  0.00686 } } },
    { "name": "lithium"             , "jvm": 0, "actual": { "ec2": { "rps":    388, "projected":    264, "conc":   8, "lat": 0.03029 }, "ded": { "rps":   2546, "projected":   2607, "conc":  32, "lat":  0.01227 } } },
    { "name": "micromvc"            , "jvm": 0, "actual": { "ec2": { "rps":   1639, "projected":   1429, "conc":  32, "lat": 0.02238 }, "ded": { "rps":  11846, "projected":   7980, "conc":  32, "lat":  0.00401 } } },
    { "name": "mojolicious"         , "jvm": 0, "actual": { "ec2": { "rps":    706, "projected":    695, "conc":   8, "lat":  0.0115 }, "ded": { "rps":   3998, "projected":   3921, "conc":  16, "lat":  0.00408 } } },
    { "name": "netty"               , "jvm": 1, "actual": { "ec2": { "rps":  29317, "projected":  31219, "conc": 128, "lat":  0.0041 }, "ded": { "rps": 184826, "projected": 182857, "conc": 256, "lat":   0.0014 } } },
    { "name": "nodejs"              , "jvm": 0, "actual": { "ec2": { "rps":   9782, "projected":   7664, "conc": 128, "lat":  0.0167 }, "ded": { "rps":  66529, "projected":  60377, "conc":  64, "lat":  0.00106 } } },
    { "name": "onion"               , "jvm": 0, "actual": { "ec2": { "rps":  42716, "projected":  37101, "conc": 128, "lat": 0.00345 }, "ded": { "rps": 193801, "projected": 228571, "conc": 256, "lat":  0.00112 } } },
    { "name": "openresty"           , "jvm": 0, "actual": { "ec2": { "rps":  31725, "projected":  34501, "conc": 256, "lat": 0.00742 }, "ded": { "rps": 187478, "projected": 182857, "conc": 256, "lat":   0.0014 } } },
    { "name": "phalcon"             , "jvm": 0, "actual": { "ec2": { "rps":   1958, "projected":   1587, "conc":  16, "lat": 0.01008 }, "ded": { "rps":  14400, "projected":  14814, "conc":  32, "lat":  0.00216 } } },
    { "name": "phalcon-micro"       , "jvm": 0, "actual": { "ec2": { "rps":   2576, "projected":   1839, "conc":  32, "lat":  0.0174 }, "ded": { "rps":  19866, "projected":  20253, "conc":  32, "lat":  0.00158 } } },
    { "name": "php"                 , "jvm": 0, "actual": { "ec2": { "rps":   3918, "projected":   3760, "conc":  64, "lat": 0.01702 }, "ded": { "rps":  35651, "projected":  28100, "conc": 256, "lat":  0.00911 } } },
    { "name": "play"                , "jvm": 0, "actual": { "ec2": { "rps":   4741, "projected":   4841, "conc": 256, "lat": 0.05288 }, "ded": { "rps":  25232, "projected":  25196, "conc":  32, "lat":  0.00127 } } },
    { "name": "play-scala"          , "jvm": 1, "actual": { "ec2": { "rps":   7889, "projected":   8139, "conc": 256, "lat": 0.03145 }, "ded": { "rps":  40424, "projected":  39950, "conc":  32, "lat": 0.000801 } } },
    { "name": "play1"               , "jvm": 1, "actual": { "ec2": { "rps":   1805, "projected":   1800, "conc":  64, "lat": 0.03554 }, "ded": { "rps":  12015, "projected":  12337, "conc": 256, "lat":  0.02075 } } },
    { "name": "play1siena"          , "jvm": 1, "actual": { "ec2": { "rps":   8653, "projected":   8648, "conc":  64, "lat":  0.0074 }, "ded": { "rps":  68037, "projected":  66666, "conc":  64, "lat":  0.00096 } } },
    { "name": "rack-jruby"          , "jvm": 1, "actual": { "ec2": { "rps":   3935, "projected":   4135, "conc": 128, "lat": 0.03095 }, "ded": { "rps":  23661, "projected":  21843, "conc": 128, "lat":  0.00586 } } },
    { "name": "rack-ruby"           , "jvm": 0, "actual": { "ec2": { "rps":   6655, "projected":   6286, "conc":  64, "lat": 0.01018 }, "ded": { "rps":  53937, "projected":  53002, "conc": 256, "lat":  0.00483 } } },
    { "name": "rails-jruby"         , "jvm": 1, "actual": { "ec2": { "rps":    748, "projected":    949, "conc":  64, "lat": 0.06739 }, "ded": { "rps":   3938, "projected":   3902, "conc":   8, "lat":  0.00205 } } },
    { "name": "rails-ruby"          , "jvm": 0, "actual": { "ec2": { "rps":    873, "projected":    897, "conc":  16, "lat": 0.01783 }, "ded": { "rps":   4206, "projected":   4199, "conc":  16, "lat":  0.00381 } } },
    { "name": "rails-stripped-jruby", "jvm": 1, "actual": { "ec2": { "rps":    910, "projected":   1137, "conc":  64, "lat": 0.05628 }, "ded": { "rps":   4746, "projected":   4678, "conc":   8, "lat":  0.00171 } } },
    { "name": "rails-stripped-ruby" , "jvm": 0, "actual": { "ec2": { "rps":   1090, "projected":   1101, "conc":  16, "lat": 0.01453 }, "ded": { "rps":   5467, "projected":   5460, "conc":  16, "lat":  0.00293 } } },
    { "name": "restexpress"         , "jvm": 1, "actual": { "ec2": { "rps":  15120, "projected":   8839, "conc": 256, "lat": 0.02896 }, "ded": { "rps": 150109, "projected":  28070, "conc": 256, "lat":  0.00912 } } },
    { "name": "ringojs"             , "jvm": 0, "actual": { "ec2": { "rps":   6400, "projected":   5196, "conc": 256, "lat": 0.04926 }, "ded": { "rps":  43691, "projected":  35955, "conc": 128, "lat":  0.00356 } } },
    { "name": "ringojs-convinient"  , "jvm": 0, "actual": { "ec2": { "rps":   5387, "projected":   4278, "conc": 256, "lat": 0.05984 }, "ded": { "rps":  34512, "projected":  26391, "conc": 128, "lat":  0.00485 } } },
    { "name": "scalatra"            , "jvm": 1, "actual": { "ec2": { "rps":  10121, "projected":   9552, "conc":  64, "lat":  0.0067 }, "ded": { "rps":  86418, "projected":  60952, "conc": 128, "lat":   0.0021 } } },
    { "name": "servlet"             , "jvm": 1, "actual": { "ec2": { "rps":  22735, "projected":  20578, "conc":  64, "lat": 0.00311 }, "ded": { "rps": 212845, "projected": 200000, "conc": 256, "lat":  0.00128 } } },
    { "name": "silex"               , "jvm": 0, "actual": { "ec2": { "rps":    370, "projected":    330, "conc":   8, "lat": 0.02421 }, "ded": { "rps":   2245, "projected":   2269, "conc":  16, "lat":  0.00705 } } },
    { "name": "sinatra-jruby"       , "jvm": 1, "actual": { "ec2": { "rps":    596, "projected":    689, "conc":  32, "lat": 0.04642 }, "ded": { "rps":   2730, "projected":   2702, "conc":   8, "lat":  0.00296 } } },
    { "name": "sinatra-ruby"        , "jvm": 0, "actual": { "ec2": { "rps":    898, "projected":    905, "conc": 256, "lat": 0.28258 }, "ded": { "rps":   1237, "projected":   1214, "conc":  16, "lat":  0.01317 } } },
    { "name": "slim"                , "jvm": 0, "actual": { "ec2": { "rps":    681, "projected":    530, "conc":   8, "lat": 0.01509 }, "ded": { "rps":   4557, "projected":   4591, "conc":  32, "lat":  0.00697 } } },
    { "name": "snap"                , "jvm": 0, "actual": { "ec2": { "rps":   7228, "projected":   7305, "conc":  64, "lat": 0.00876 }, "ded": { "rps":  26029, "projected":  25396, "conc":  32, "lat":  0.00126 } } },
    { "name": "spark"               , "jvm": 1, "actual": { "ec2": { "rps":  19598, "projected":  18550, "conc":  64, "lat": 0.00345 }, "ded": { "rps": 202181, "projected": 103225, "conc": 256, "lat":  0.00248 } } },
    { "name": "spray"               , "jvm": 1, "actual": { "ec2": { "rps":  33834, "projected":  34133, "conc": 256, "lat":  0.0075 }, "ded": { "rps": 196797, "projected": 201574, "conc": 256, "lat":  0.00127 } } },
    { "name": "spring"              , "jvm": 1, "actual": { "ec2": { "rps":   8732, "projected":   8533, "conc": 128, "lat":   0.015 }, "ded": { "rps":  67368, "projected":  56888, "conc": 128, "lat":  0.00225 } } },
    { "name": "tapestry"            , "jvm": 1, "actual": { "ec2": { "rps":  10097, "projected":   9907, "conc": 128, "lat": 0.01292 }, "ded": { "rps":  99372, "projected":  64000, "conc": 128, "lat":    0.002 } } },
    { "name": "tornado"             , "jvm": 0, "actual": { "ec2": { "rps":   3905, "projected":   3921, "conc":  64, "lat": 0.01632 }, "ded": { "rps":  21990, "projected":  16284, "conc":  64, "lat":  0.00393 } } },
    { "name": "unfiltered"          , "jvm": 1, "actual": { "ec2": { "rps":  28710, "projected":  27735, "conc": 256, "lat": 0.00923 }, "ded": { "rps": 144115, "projected": 141436, "conc": 256, "lat":  0.00181 } } },
    { "name": "vertx"               , "jvm": 1, "actual": { "ec2": { "rps":  23502, "projected":  20545, "conc": 256, "lat": 0.01246 }, "ded": { "rps": 119629, "projected": 113274, "conc": 256, "lat":  0.00226 } } },
    { "name": "wai"                 , "jvm": 0, "actual": { "ec2": { "rps":  17465, "projected":   9570, "conc": 256, "lat": 0.02675 }, "ded": { "rps": 124467, "projected": 101748, "conc":  64, "lat": 0.000629 } } },
    { "name": "web-simple"          , "jvm": 0, "actual": { "ec2": { "rps":   2119, "projected":   2113, "conc":  64, "lat": 0.03028 }, "ded": { "rps":  15451, "projected":  15590, "conc": 128, "lat":  0.00821 } } },
    { "name": "webgo"               , "jvm": 0, "actual": { "ec2": { "rps":  13584, "projected":  14446, "conc":  64, "lat": 0.00443 }, "ded": { "rps":  79752, "projected":  85906, "conc": 128, "lat":  0.00149 } } },
    { "name": "wicket"              , "jvm": 1, "actual": { "ec2": { "rps":  12290, "projected":  11710, "conc": 128, "lat": 0.01093 }, "ded": { "rps":  76846, "projected":  77811, "conc": 256, "lat":  0.00329 } } },
    { "name": "wsgi"                , "jvm": 0, "actual": { "ec2": { "rps":   9577, "projected":   8121, "conc": 128, "lat": 0.01576 }, "ded": { "rps":  60097, "projected":  64321, "conc": 128, "lat":  0.00199 } } },
    { "name": "yesod"               , "jvm": 0, "actual": { "ec2": { "rps":   8551, "projected":   6889, "conc": 256, "lat": 0.03716 }, "ded": { "rps":  64299, "projected":  48484, "conc": 256, "lat":  0.00528 } } }
  ];

  // config
  var w = 680
  var h = 400
  var xPadding = 30
  var yPadding = 20
  var labellingThresholdY = 13000
  var trendlineX1 = 50000
  var trendlineX2 = 230000

  // impl
  var xScale = d3.scale.linear().domain([0, 240000]).range([xPadding, w - xPadding]);
  var yScale = d3.scale.linear().domain([0, 50000]).range([h - yPadding, yPadding]);

  var axesFormat = d3.format("s");
  var xAxis = d3.svg.axis().scale(xScale).orient("bottom").ticks(10).tickFormat(axesFormat);
  var yAxis = d3.svg.axis().scale(yScale).orient("left").ticks(10).tickFormat(axesFormat);

  var svg = d3.select("svg").style("width", w).style("height", h);

  var actualTrend = simpleLinearRegression(dataset.map(function(d) { return { x: d.actual.ded.rps, y: d.actual.ec2.rps } }));
  var projectedTrend = simpleLinearRegression(dataset.map(function(d) { return { x: d.actual.ded.projected, y: d.actual.ec2.projected } }));
  function applyTrendline(line, trend) {
    return line.attr("x1", xScale(trendlineX1))
      .attr("y1", yScale(trend(trendlineX1)))
      .attr("x2", xScale(trendlineX2))
      .attr("y2", yScale(trend(trendlineX2)));
  }
  var trendLine = applyTrendline(svg.append("svg:line").attr("class", "trend"), actualTrend);

  function forActual(d) { return xScale(d.actual.ded.rps) + "," + yScale(d.actual.ec2.rps) }
  function forProjected(d) { return xScale(d.actual.ded.projected) + "," + yScale(d.actual.ec2.projected) }

  function transition(selection) { return selection.transition().delay(0).duration(400) }

  var groups = svg.selectAll("g").data(dataset);
  groups.enter().append("svg:g")
      .attr("class", function(d) { return d.name == "spray" ? "spray data-point actual" : "data-point actual" })
      .attr("transform", function(d) { return "translate(" + forActual(d) + ")" });
  groups.append("svg:circle").attr("r", 5)
      .attr("class", function(d) { return d.jvm ? "jvm" : "no-jvm" })
      .on("mouseover", function() { transition(d3.select(this)).ease("elastic").attr("r", 8).attr("stroke-width", "2px"); })
      .on("mouseout",  function() { transition(d3.select(this)).ease("elastic").attr("r", 5).attr("stroke-width", "0"); });
  groups.append("svg:text")
      .attr("dx", 8)
      .attr("dy", ".35em")
      .text(function(d) { return d.actual.ec2.rps > labellingThresholdY ? d.name: ""; });

  svg.append("svg:g").attr("class", "axis").attr("transform", "translate(0," + (h - yPadding) + ")").call(xAxis);
  svg.append("svg:g").attr("class", "axis").attr("transform", "translate(" + xPadding + ",0)").call(yAxis);
  svg.append("text")
      .attr("class", "axis-label")
      .attr("text-anchor", "end")
      .attr("x", w - xPadding)
      .attr("y", h - yPadding - 5)
      .text("peak requests/sec (dedicated hardware)");
  svg.append("text")
      .attr("class", "axis-label")
      .attr("text-anchor", "end")
      .attr("x", -yPadding)
      .attr("y", xPadding + 5)
      .attr("dy", ".75em")
      .attr("transform", "rotate(-90)")
      .text("peak requests/sec (EC2 m1.large)");

  var reqFormat = d3.format(".3s");
  $('g.actual circle')
      .data('powertip', function() {
        var d = this.__data__
        var act = $('#actual').attr("checked");
        function tier(n, t) { return n + ": " + reqFormat(act ? t.rps : t.projected) + " rps at " + t.conc + " conns" }
        return "<b>" + d.name + "</b><br/>" + tier("EC2", d.actual.ec2) + "<br/>" + tier("i7", d.actual.ded)
      })
      .powerTip({ fadeInTime:	100 });

  function updatePositions(f) { transition(groups).attr("transform", function(d) { return "translate(" + f(d) + ")" }) }

  d3.select("#actual").on("change", function() {
    updatePositions(forActual);
    applyTrendline(transition(trendLine), actualTrend);
  });
  d3.select("#projected").on("change", function() {
    updatePositions(forProjected);
    applyTrendline(transition(trendLine), projectedTrend);
  });
});