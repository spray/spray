_json-lenses_ is a library to query and update [JSON] data structures.

It has the following features

 * A type-safe syntax to address elements in a json data structure
 * Retrieve addressed elements
 * Easily update addressed elements of the immutable spray-json representation
 * Addressed elements can be of several cardinalities: scalar values, optional
   values, and sequences of values
 * Experimental support for [json-path] syntax

## Usage

If you use SBT you can include _json-lenses_ in your project with

    "net.virtual-void" %%  "json-lenses" % "0.5.0"

## Example

Given this example json document:
```scala
val json = """
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
}""".asJson
```

All authors in this document are addressed by
```scala
import cc.spray.json.lenses.JsonLenses._

val allAuthors = 'store / 'book / * / 'author
```

This is called a lens. You can use a lens to retrieve or update the addressed
values.

```scala
val authorNames = json.extract[String](allAuthors)
```

To update values use one of the defined operations. To overwrite a value use `set`, to change a value
based on the previous value use `modify`.

```scala
// overwrite all authors' names to "John Doe"
val newJson1 = json.update(allAuthors ! set[String]("John Doe"))

// prepend authors' names with "Ms or Mr "
val newJson2 = json.update(allAuthors ! modify[String]("Ms or Mr " + _))
```

## Documentation

### The concept

The concept of lenses is a powerful concept not only applicable to json objects. A lens is 
an updatable, composable view into a data structure. There are simple lenses which provide
just the functionality to "go one level deeper" in the data structure (for json: accessing fields of a
json object or elements of a json array) and there are lenses which compose other lenses
to allow a deeper view into the data structure.

See [this answer][so-lenses] on stack overflow and [this presentation][lenses] for more info on the
general topic of lenses.


#### The json lens

The json lenses in this project are specialized lenses to extract and update json data. A lens has
this form (almost, in reality it's a bit more complicated):

```scala
// not the real code
trait Lens {
  def retr: JsValue => JsValue
  def updated(f: JsValue => JsValue)(parent: JsValue): JsValue
}
```

It has a method `retr` which when called with a json value will return the value this lens points to.
The other method `updated` takes a parent json value and returns an updated copy of it with function `f` 
applied to the child value this lens points to.

In contrast to a more general lens input and output types of the json lens are fixed: both have to be an
instance of `JsValue`.

#### Support for multiple cardinalities

The simple scheme introduced in the last section is actually too simple to support more than the absolute
simplest lenses. One basic requirement is to extract a list of values or an optional value. Therefore, lenses
are parameterized by the container type for the cardinality of the lens.

```scala
// still not the real code
trait Lens[M[_]] {
  def retr: JsValue => M[JsValue]
  // ...
```

Scalar lenses are of type `Lens[Id]` (`Id` being defined as the identity type constructor, so `Id[T] == T`).
Optional lenses are of type `Lens[Option]`. Lenses returning a sequence of values are of type `Lens[Seq]`. An
interesting thing happens when two lenses of different cardinality are joined: The rule is to always return a lens
of the more general container type, i.e. the one with the greater cardinality. See the source code of `JsonLenses.combine`
and the `Join` type class to see how this is done.

#### Error handling

To support proper error handling and recovery from errors (in some cases) failure is is always assumed as a
possible outcome. This is reflected by returning an `Either` value from almost all functions which may fail. The
real declaration of the `Lens` type therefore looks more like this:

```scala
type Validated[T] = Either[Exception, T]
type SafeJsValue = Validated[JsValue]

trait Lens[M[_]] {
  def retr: JsValue => Validated[M[JsValue]]
  def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue
}
```

The result of `retr` is not always a `Right(jsValue)` but may fail with `Left(exception)` as well. The same
for `updated`: the update operation may also fail. However, in the update case there are two other possibilities
encoded as well: Retrieval of a value may fail but the update operation `f` may still succeed (for example, when
setting a previously undefined value) or the update operation itself fails in which case the complete update
operation fails as well.

#### Predefined lenses

When working with lenses you normally don't have to worry about the details but you can just choose and combine lenses
from the following list.

##### Field access
 * `field(name: String)`: This lens assumes that the target value is a json object and selects the field
   with the specified name. Because this is the most common lens there are shortcut implicit conversions defined
   from `String` and `Symbol` values.

##### Element access

 * `elements` or `*`: This lens selects all the elements of a json array. If you combine this lens with another
   one you have to make sure that the next lens will match for all the elements of the array, otherwise it is an
   error. Use `allMatching` if you want to exclude elements not matching nested lenses.
 * `element(idx: Int)`: Selects the element of a json array with the given index.
 * `find(predicate: JsValue => Boolean)`: Selects all elements of a json array which match a certain predicated.
   In the common case you don't want to work directly on `JsValue`s so you can use lenses as well to define the
   predicate. Use `Lens.is[T](pred: T => Boolean)` to lift a predicate from the value level to the `JsValue` level.
   E.g. use `'fullName.is[String](_ startsWith "Joe")` to create a predicate which checks if a value is a json object
   and its field `fullName` is a string starting with `"Joe"`.
 * `allMatching(next: Lens)`: A combination of `combine` and `elements`. Selects elements matching the `next` lens 
   and then applies the `next` lens.

##### Combination

 * `combine`: This lens combines two lenses by executing the second one on the result of the first one.
   Use it to access nested elements. Because its use is so common there's a shortcut operator, `Lens./`,
   which you can use to 'chain' lenses: e.g. use `'abc / 'def` access field 'def' inside the object in field 'abc'
   of the root json value passed to the lens.

#### Predefined update operations

Currently, there are these update operations defined:

 * `set[T: JsonWriter](t: => T)` uses the `JsonWriter` to set the value to a constant value.
 * `modify[T: JsonFormat](f: T => T)` applies an update function to an existing value. The value has to be
   serializable from and to `T`.

#### Using lenses to extract or update json data

To extract a value from json data using a lens you can use either `lens.get[T](json)` or the equivalent
`json.extract[T](lens)` which both throw an exception in case something fails or use `lens.tryGet[T](json)`
which returns an `Either` value you can match on (or use `Either`'s functions) to check for errors.

To update a value use either `(lens ! operation).apply(json)` or `json.update(lens ! operation)`. You can
also use the fancy `val newJson = json(lens) = "abc"` to set a value if you like that syntax. These operations 
throw an exception in the error case. Use `lens.updated(operation)(json)` to get an `Either` result to process errors.

#### [json-path] support

Use `JsonLenses.fromPath` which tries to create a lens from a json-path expression. Currently not all
of the syntax of json-path is supported. Please file an issue if something is missing.

## API Documentation

You can find the documentation for the _json-lenses_ API here:
<http://jrudolph.github.com/json-lenses/api>

## Future

It is planned that the json lenses will eventually move out of their incubator and
will then be integrated into [spray-json] proper.

## License

_spray-json_ is licensed under [APL 2.0].

[APL 2.0]: http://www.apache.org/licenses/LICENSE-2.0
[JSON]: http://json.org
[spray-json]: https://github.com/spray/spray-json
[json-path]: http://goessner.net/articles/JsonPath/
[so-lenses]: http://stackoverflow.com/a/5597750/7647
[lenses]: http://www.cis.upenn.edu/~bcpierce/papers/lenses-etapsslides.pdf