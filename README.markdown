## _spray-json_

_spray-json_ is a lightweight, clean and simple [JSON][] implementation in Scala.

It sports the following features:
  
* Simple immutable model of the JSON language elements
* An efficient JSON PEG parser (implemented with [parboiled][])
* Choice of either compact or pretty JSON to-string-printing
* Type-class based (de)serialization of custom objects (no reflection, no intrusion)


### Installation

_spray-json_ is available from the [scala-tools.org][] respositories.
There is no official release yet, but the current snapshot is `1.0-SNAPSHOT`.
If you use SBT you can include it in your project with

    val sprayJsonModuleConfig = ModuleConfiguration("cc.spray", ScalaToolsSnapshots)
    val sprayJson = "cc.spray" %% "spray-json" % "1.0.0-SNAPSHOT" % "compile" withSources()

_spray-json_ has only one dependency: the parsing library [parboiled][] (which is also a dependency of _spray-server_ and _spray_client_, so if you use _spray-json_ with either of them you are not incurring any additional dependency).


### Usage

_spray-json_ is really easy to use.
Just bring all relevant elements in scope with 

    import cc.spray.json._

and do one or more of the following things:

1. Parse a JSON string into it's Abstract Syntax Tree (AST) representation

        val json = """{ "some": "JSON source" }""" 
        val jsonAst = JsonParser(json)
    
2. Print a JSON AST back to a String using either the `CompactPrinter` or the `PrettyPrinter`

        val json = PrettyPrinter(jsonAst)
        
3. Convert any Scala object to a JSON AST using the pimped "toJson" method 
        
        val jsonAst = List(1, 2, 3).toJson 

4. Convert a JSON AST to a Scala object with the "fromJson" method

        val myObject = jsonAst.fromJson[MyObjectType]
        
   or 

        val myObject: MyObjectType = jsonAst.fromJson

        
In order to make steps 3 and 4 work for an object of type `T` you need to bring implicit values in scope that
provide `JsonFormat[T]`s for `T` and all types used by `T` (directly or indirectly).
The way you normally do this is via a "JsonProtocol".


### JsonProtocol

_spray-json_ uses [SJSON][]s Scala-idiomatic type-class based approach to connect an existing type `T` with the information how to (de)serialize its instances to and from JSON. (In fact _spray-json_ even reuses [SJSON][] code, see the 'Credits' section below).

This approach has the advantage of not requiring any change (or even access) to `T`s source code. All (de)serialization
logic is attached 'from the outside'. There is no reflection involved, so the resulting conversions are fast. Scalas excellent type inference reduces verbosity and boilerplate to a minimum, while the Scala compiler will make sure at compile time that you provided all required (de)serialization logic. 
   
In _spray-jsons_ terminology a 'JsonProtocol' is nothing but a bunch of implicit values of type `JsonFormat[T]`, whereby each `JsonFormat[T]` contains the logic of how to convert instance of `T` to and from JSON. All `JsonFormat[T]`s of a protocol need to be "MECE" (mutually exclusive, collectively exhaustive), i.e. they are not allowed to overlap and together need to span all types required by the application.
   
This may sound more complicated than it is.
_spray-json_ comes with a `DefaultJsonProtocol` which already covers all of Scalas value types as well as the most important reference and collection types. As long as your code uses nothing more than these you only need the `DefaultJsonProtocol`.
Here are the types already taken care off by the `DefaultJsonProtocol`: 
  
* Byte, Short, Int, Long, Float, Double, Char, Unit, Boolean
* String, Symbol
* BigInt, BigDecimal
* Option, Tuple1 - Tuple7
* List, Array, immutable.Map, immutable.Set, mutable.Set
 
In most cases however you'll also want to convert types not covered by the `DefaultJsonProtocol`. In these cases you need to provide `JsonFormat[T]`s for your custom types. This is not hard at all.
 

### Providing JsonFormats for Case Classes

If your custom type `T` is a case class then augmenting the `DefaultJsonProtocol` with a `JsonFormat[T]` is really easy:

    case class Color(name: String, red: Int, green: Int, blue: Int)

    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit val colorFormat = jsonFormat(Color, "name", "red", "green", "blue")     
    }
    
    import MyJsonProtocol._
    
    val json = Color("CadetBlue", 95, 158, 160).toJson
    val color = json.fromJson[Color] 
    
The `jsonFormat` method reduces the boilerplate to a minimum, just pass it the companion object of your case class as well as the field names (in order) and it will return a ready-to-use `JsonFormat` for your type.


### Providing JsonFormats for other Types

Of course you can also supply (de)serialization logic for types that aren't case classes.
Here is one way to do it:

    class Color(val name: String, val red: Int, val green: Int, val blue: Int)
    
    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit object ColorJsonFormat extends JsonFormat[Color] {
        def write(c: Color) = {
          JsArray(JsString(c.name), JsNumber(c.red), JsNumber(c.green), JsNumber(c.blue))
        }
        def read(value: JsValue) = value match {
          case JsArray(JsString(name) :: JsNumber(red) :: JsNumber(green) :: JsNumber(blue) :: Nil) => {
            new Color(name, red.toInt, green.toInt, blue.toInt)
          }
          case _ => throw new DeserializationException("Color expected")
        }
      }
    }
    
    import MyJsonProtocol._
    
    val json = Color("CadetBlue", 95, 158, 160).toJson
    val color = json.fromJson[Color]    

This serializes `Color` instances as a JSON array, which is compact but does not make the elements semantics explicit.
You need to know that the color components are ordered "red, green, blue".

Another way would be to serialize `Color`s as JSON objects:

     object MyJsonProtocol extends DefaultJsonProtocol {
      implicit object ColorJsonFormat extends JsonFormat[Color] {
        def write(c: Color) = JsObject(
          JsField("name", c.name),
          JsField("red", c.red),
          JsField("green", c.green),
          JsField("blue", c.blue)
        )
        def read(value: JsValue) = value match {
          case JsObject(
            JsField("name", JsString(name)),
            JsField("red", JsNumber(red)),
            JsField("green", JsNumber(green)),
            JsField("blue", JsNumber(blue))
          ) => {
            new Color(name.value, red.value.toInt, green.value.toInt, blue.value.toInt)
          }
          case _ => throw new DeserializationException("Color expected")
        }
      }
    }

This is a bit more verbose in its definition and the resulting JSON but transports the field semantics over to the JSON side.
Note that this is the approach _spray-json_ uses for case classes.


### Credits

Most of type-class (de)serialization code is nothing but a polished copy of what **Debasish Ghosh** made available
with his [SJSON library][]. These code parts therefore bear his copyright.
Additionally the JSON AST model is heavily inspired by the one from [Databinder-Dispatch][] by **Nathan Hamblen**.

  
### License

_spray-json_ is licensed under [APL 2.0][].


### Patch Policy

Feedback and contributions to the project, no matter what kind, are always very welcome.
However, patches can only be accepted from their original author.
Along with any patches, please state that the patch is your original work and that you license the work to the pegdown project under the project’s open source license.
  
   
  [parboiled]: http://parboiled.org
  [scala-tools.org]: http://scala-tools.org
  [SJSON]: https://github.com/debasishg/sjson
  [Databinder-Dispatch]: https://github.com/n8han/Databinder-Dispatch
  [APL 2.0]: http://www.apache.org/licenses/LICENSE-2.0
