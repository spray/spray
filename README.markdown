_spray-json_ is a lightweight, clean and efficient [JSON] implementation in Scala.

It sports the following features:
  
* Simple immutable model of the JSON language elements
* An efficient JSON PEG parser (implemented with [parboiled][])
* Choice of either compact or pretty JSON-to-string printing
* Type-class based (de)serialization of custom objects (no reflection, no intrusion)


### Installation

_spray-json_ is available from the [repo.spray.io] repository.
The latest release is `1.1.1` and is built against Scala 2.9.1.

If you use SBT you can include _spray-json_ in your project with

    "io.spray" %%  "spray-json" % "1.1.1"

_spray-json_ has only one dependency: the parsing library [parboiled][]
(which is also a dependency of _spray-server_ and _spray-client_, so if you use _spray-json_ with either of them you
are not incurring any additional dependency).


### Usage

_spray-json_ is really easy to use.  
Just bring all relevant elements in scope with 

    import spray.json._

and do one or more of the following:

1. Parse a JSON string into its Abstract Syntax Tree (AST) representation

        val source = """{ "some": "JSON source" }"""
        val jsonAst = source.asJson // or JsonParser(source)

2. Print a JSON AST back to a String using either the `CompactPrinter` or the `PrettyPrinter`

        val json = jsonAst.prettyPrint // or .compactPrint

3. Convert any Scala object to a JSON AST using the pimped `toJson` method 
        
        val jsonAst = List(1, 2, 3).toJson 

4. Convert a JSON AST to a Scala object with the `convertTo` method

        val myObject = jsonAst.convertTo[MyObjectType]

In order to make steps 3 and 4 work for an object of type `T` you need to bring implicit values in scope that
provide `JsonFormat[T]` instances for `T` and all types used by `T` (directly or indirectly).
The way you normally do this is via a "JsonProtocol".


### JsonProtocol

_spray-json_ uses [SJSON]s Scala-idiomatic type-class-based approach to connect an existing type `T` with the logic how
to (de)serialize its instances to and from JSON. (In fact _spray-json_ even reuses some of [SJSON]s code, see the
'Credits' section below).

This approach has the advantage of not requiring any change (or even access) to `T`s source code. All (de)serialization
logic is attached 'from the outside'. There is no reflection involved, so the resulting conversions are fast. Scalas
excellent type inference reduces verbosity and boilerplate to a minimum, while the Scala compiler will make sure at
compile time that you provided all required (de)serialization logic.
   
In _spray-jsons_ terminology a 'JsonProtocol' is nothing but a bunch of implicit values of type `JsonFormat[T]`, whereby
each `JsonFormat[T]` contains the logic of how to convert instance of `T` to and from JSON. All `JsonFormat[T]`s of a
protocol need to be "mece" (mutually exclusive, collectively exhaustive), i.e. they are not allowed to overlap and
together need to span all types required by the application.
   
This may sound more complicated than it is.  
_spray-json_ comes with a `DefaultJsonProtocol`, which already covers all of Scalas value types as well as the most
important reference and collection types. As long as your code uses nothing more than these you only need the
`DefaultJsonProtocol`. Here are the types already taken care of by the `DefaultJsonProtocol`:
  
* Byte, Short, Int, Long, Float, Double, Char, Unit, Boolean
* String, Symbol
* BigInt, BigDecimal
* Option, Either, Tuple1 - Tuple7
* List, Array
* immutable.{Map, Iterable, Seq, IndexedSeq, LinearSeq, Set, Vector}
* collection.{Iterable, Seq, IndexedSeq, LinearSeq, Set}
* JsValue

In most cases however you'll also want to convert types not covered by the `DefaultJsonProtocol`. In these cases you
need to provide `JsonFormat[T]`s for your custom types. This is not hard at all.
 

### Providing JsonFormats for Case Classes

If your custom type `T` is a case class then augmenting the `DefaultJsonProtocol` with a `JsonFormat[T]` is really easy:

    case class Color(name: String, red: Int, green: Int, blue: Int)

    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit val colorFormat = jsonFormat4(Color)
    }
    
    import MyJsonProtocol._
    
    val json = Color("CadetBlue", 95, 158, 160).toJson
    val color = json.convertTo[Color]
    
The `jsonFormatX` methods reduce the boilerplate to a minimum, just pass the right one the companion object of your
case class and it will return a ready-to-use `JsonFormat` for your type (the right one is the one matching the number
of arguments to your case class constructor, e.g. if your case class has 13 fields you need to use the `jsonFormat13`
method). The `jsonFormatX` methods try to extract the field names of your case class before calling the more general
`jsonFormat` overloads, which let you specify the field name manually. So, if spray-json has trouble determining the
field names or if your JSON objects use member names that differ from the case class fields you can also use
`jsonFormat` directly.

There is one additional quirk: If you explicitly declare the companion object for your case class the notation above will
stop working. You'll have to explicitly refer to the companion objects `apply` method to fix this:

    case class Color(name: String, red: Int, green: Int, blue: Int)
    object Color

    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit val colorFormat = jsonFormat4(Color.apply)
    }

If your case class is generic in that it takes type parameters itself the `jsonFormat` methods can also help you.
However, there is a little more boilerplate required as you need to add context bounds for all type parameters
and explicitly refer to the case classes `apply` method as in this example:

    case class NamedList[A](name: String, items: List[A])

    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit def namedListFormat[A :JsonFormat] = jsonFormat2(NamedList.apply[A])
    }


#### NullOptions

The `NullOptions` trait supplies an alternative rendering mode for optional case class members. Normally optional
members that are undefined (`None`) are not rendered at all. By mixing in this trait into your custom JsonProtocol you
can enforce the rendering of undefined members as `null`.
(Note that this only affect JSON writing, spray-json will always read missing optional members as well as `null`
optional members as `None`.)


### Providing JsonFormats for other Types

Of course you can also supply (de)serialization logic for types that aren't case classes.  
Here is one way to do it:

    class Color(val name: String, val red: Int, val green: Int, val blue: Int)
    
    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit object ColorJsonFormat extends RootJsonFormat[Color] {
        def write(c: Color) =
          JsArray(JsString(c.name), JsNumber(c.red), JsNumber(c.green), JsNumber(c.blue))

        def read(value: JsValue) = value match {
          case JsArray(JsString(name) :: JsNumber(red) :: JsNumber(green) :: JsNumber(blue) :: Nil) =>
            new Color(name, red.toInt, green.toInt, blue.toInt)
          case _ => deserializationError("Color expected")
        }
      }
    }
    
    import MyJsonProtocol._
    
    val json = Color("CadetBlue", 95, 158, 160).toJson
    val color = json.convertTo[Color]

This serializes `Color` instances as a JSON array, which is compact but does not make the elements semantics explicit.
You need to know that the color components are ordered "red, green, blue".

Another way would be to serialize `Color`s as JSON objects:

    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit object ColorJsonFormat extends RootJsonFormat[Color] {
        def write(c: Color) = JsObject(
          "name" -> JsString(c.name),
          "red" -> JsNumber(c.red),
          "green" -> JsNumber(c.green),
          "blue" -> JsNumber(c.blue)
        )
        def read(value: JsValue) = {
          value.asJsObject.getFields("name", "red", "green", "blue") match {
            case Seq(JsString(name), JsNumber(red), JsNumber(green), JsNumber(blue)) =>
              new Color(name, red.toInt, green.toInt, blue.toInt)
            case _ => throw new DeserializationException("Color expected")
          }
        }
      }
    }

This is a bit more verbose in its definition and the resulting JSON but transports the field semantics over to the
JSON side. Note that this is the approach _spray-json_ uses for case classes.


### JsonFormat vs. RootJsonFormat

According to the JSON specification not all of the defined JSON value types are allowed at the root level of a JSON
document. A JSON string for example (like `"foo"`) does not constitute a legal JSON document by itself.
Only JSON objects or JSON arrays are allowed as JSON document roots.

In order to distinguish, on the type-level, "regular" JsonFormats from the ones producing root-level JSON objects or
arrays _spray-json_ defines the [`RootJsonFormat`][1] type, which is nothing but a marker specialization of `JsonFormat`.
Libraries supporting _spray-json_ as a means of document serialization might choose to depend on a `RootJsonFormat[T]`
for a custom type `T` (rather than a "plain" `JsonFormat[T]`), so as to not allow the rendering of illegal document
roots. E.g., the `SprayJsonSupport` trait of _spray-server_ is one notable example of such a case.

All default converters in the `DefaultJsonProtocol` producing JSON objects or arrays are actually implemented as
`RootJsonFormat`. When "manually" implementing a `JsonFormat` for a custom type `T` (rather than relying on case class
support) you should think about whether you'd like to use instances of `T` as JSON document roots and choose between
a "plain" `JsonFormat` and a `RootJsonFormat` accordingly.

  [1]: http://spray.github.com/spray/api/spray-json/cc/spray/json/RootJsonFormat.html


### JsonFormats for recursive Types

If your type is recursive such as

    case class Foo(i: Int, foo: Foo)

you need to wrap your format constructor with `lazyFormat` and supply an explicit type annotation:

    implicit val fooFormat: JsonFormat[Foo] = lazyFormat(jsonFormat(Foo, "i", "foo"))

Otherwise your code will either not compile (no explicit type annotation) or throw an NPE at runtime (no `lazyFormat`
wrapper).


### API Documentation

You can find the documentation for the _spray-json_ API here:
<http://spray.github.com/spray/api/spray-json/>


### Credits

Most of type-class (de)serialization code is nothing but a polished copy of what **Debasish Ghosh** made available
with his [SJSON] library. These code parts therefore bear his copyright.
Additionally the JSON AST model is heavily inspired by the one contributed by **Jorge Ortiz** to [Databinder-Dispatch].

  
### License

_spray-json_ is licensed under [APL 2.0].


### Patch Policy

Feedback and contributions to the project, no matter what kind, are always very welcome.
However, patches can only be accepted from their original author.
Along with any patches, please state that the patch is your original work and that you license the work to the
_spray-json_ project under the project’s open source license.
  

  [JSON]: http://json.org
  [parboiled]: http://parboiled.org
  [repo.spray.io]: http://repo.spray.io
  [SJSON]: https://github.com/debasishg/sjson
  [Databinder-Dispatch]: https://github.com/n8han/Databinder-Dispatch
  [APL 2.0]: http://www.apache.org/licenses/LICENSE-2.0
