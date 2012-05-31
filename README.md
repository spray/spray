_json-lenses_ is a library to query and update [JSON] data structures.

It has the following features

 * A type-safe syntax to address elements in a json data structure
 * Retrieve or update the addressed elements
 * Addressed elements can be of several cardinalities: scalar values, optional
   values, and sequences of values
 * Experimental support for [json-path] syntax

### Examples

Given this example json document:
{{{
{ "store": {
    "book": [
      { "category": "reference",
        "author": "Nigel Rees",
        "title": "Sayings of the Century",
        "price": 8.95
      },
      { "category": "fiction",
        "author": "Evelyn Waugh",
        "title": "Sword of Honour",
        "price": 12.99,
        "isbn": "0-553-21311-3"
      }
    ],
    "bicycle": {
      "color": "red",
      "price": 19.95
    }
  }
}
}}}

### Installation

If you use SBT you can include _json-lenses_ in your project with

    "net.virtual-void" %%  "json-lenses" % "0.5.0"

### API Documentation

You can find the documentation for the _json-lenses_ API here:
<http://jrudolph.github.com/json-lenses/api>

### Future

It is planned that the json lenses will eventually move out of their incubator and
will then be integrated into [spray-json] proper.

### License

_spray-json_ is licensed under [APL 2.0].

[APL 2.0]: http://www.apache.org/licenses/LICENSE-2.0
[JSON]: http://json.org
[spray-json]: https://github.com/spray/spray-json
[json-path]: http://goessner.net/articles/JsonPath/