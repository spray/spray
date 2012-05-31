name := "json-lenses"

organization := "net.virtual-void"

homepage := Some(url("http://github.com/jrudolph/json-lenses"))

licenses in GlobalScope += "Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")

(LsKeys.tags in LsKeys.lsync) := Seq("json", "spray", "lens", "json-path")

(LsKeys.docsUrl in LsKeys.lsync) <<= homepage

(description in LsKeys.lsync) :=
  "json-lenses is a library to query and update JSON data structures."
