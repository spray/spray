package spray.json

import reflect.macros.Context
import org.parboiled.errors.{ ErrorUtils, ParsingException }

object InterpolatorMacro {
  def jsonMacro(c: Context)(args: c.Expr[Any]*): c.Expr[JsValue] = {
    val outerContext = c
    val outerArgs = args
    object Impl extends InterpolatorMacroHelper {
      val c = outerContext
      val args = outerArgs.asInstanceOf[Seq[c.Expr[Any]]]
      import c._
      import c.universe._

      def run = {
        def componentString(comp: Component): String = comp match {
          case LiteralStringComponent(str) ⇒ str
          case ExpressionComponent(_, id)  ⇒ "###(" + id + ")###"
        }

        val exprString = components.map(componentString).mkString
        val expr = JsValueExprParser.parseExpr(exprString.toCharArray)
        //println(expr, args.headOption.map(_.tree))

        import JsValueAST._
        def build(valueExpr: JsValueExpr): c.Expr[JsValue] = valueExpr match {
          case LiteralJsValueExpr(JsString(str)) ⇒ reify(JsString(literal(str).splice))
          case LiteralJsValueExpr(JsNumber(num)) ⇒ reify(JsNumber(literal(num.toString).splice))
          case LiteralJsValueExpr(JsTrue)        ⇒ reify(JsTrue)
          case LiteralJsValueExpr(JsFalse)       ⇒ reify(JsFalse)
          case LiteralJsValueExpr(JsNull)        ⇒ reify(JsNull)
          case JsObjectExpr(keyValues) ⇒
            def buildKv(kvExpr: KeyValuesExpr): c.Expr[Seq[(String, JsValue)]] = kvExpr match {
              case SingleKeyValueExpr(keyExpr, valueExpr) ⇒
                reify(Seq((buildLabel(keyExpr).splice, build(valueExpr).splice)))

              // ObjectFieldFormat(label).fromValue(valueExpr)
              /*c.Expr[Seq[(String, JsValue)]] {
                  Apply(
                    Select(Apply(Ident(newTermName("ObjectFieldFormat")), List(buildLabel(keyExpr).tree)), newTermName("fromValue")),
                    List(build(valueExpr).tree))
                }*/
            }
            def buildLabel(labelExpr: LabelExpr): c.Expr[String] = labelExpr match {
              case ConstantLabelExpr(label) ⇒ literal(label)
              case ExternalStringExpr(IdExprRef(id)) ⇒
                c.Expr[String](args(id).tree)
            }

            val comps = liftSeq(keyValues.map(buildKv))
            reify(JsObject(comps.splice.flatten: _*))

          case JsArrayExpr(elementExprs) ⇒
            val comps = liftSeq(elementExprs.map(build))
            reify(JsArray(comps.splice))

          case ExternalJsValueExpr(IdExprRef(id)) ⇒
            val expr = args(id)

            c.Expr[JsValue](atPos(expr.tree.pos)(Select(expr.tree, newTermName("toJson"))))
          case _ ⇒ reify(JsNumber(23))
        }

        build(expr)
      }
    }

    Impl.run.asInstanceOf[c.Expr[JsValue]]
  }

  trait InterpolatorMacroHelper {
    val c: Context
    val args: Seq[c.Expr[Any]]
    import c.universe._

    sealed trait Component
    case class LiteralStringComponent(text: String) extends Component
    case class ExpressionComponent(expr: c.Expr[Any], index: Int) extends Component

    lazy val Apply(_, List(Apply(_, stringExprs))) = c.prefix.tree
    lazy val stringParts = (stringExprs: List[Tree]) map {
      case Literal(Constant(s: String)) ⇒ s
      case x @ _                        ⇒ println(x.getClass.getSimpleName + ": " + x); "n/a"
    }
    def liftSeq[T](els: Seq[c.Expr[T]]): c.Expr[Seq[T]] =
      c.Expr[Seq[T]](Apply(Ident(definitions.List_apply), els.map(_.tree).toList))

    def components: Seq[Component] = {
      val stringComps = stringParts.map(LiteralStringComponent)
      val exprComps = args.zipWithIndex.map((ExpressionComponent.apply _).tupled)
      (stringComps, exprComps).zipped.flatMap {
        case (a, b) ⇒ Seq(a, b)
      } ++ (if (stringComps.size > args.size) Seq(stringComps.last) else Nil)
    }
  }
}

object JsValueAST {
  sealed trait JsValueExpr
  case class LiteralJsValueExpr(jsValue: JsValue) extends JsValueExpr
  case class JsArrayExpr(elements: Seq[JsValueExpr]) extends JsValueExpr
  case class JsObjectExpr(keyValues: Seq[KeyValuesExpr]) extends JsValueExpr
  case class ExternalJsValueExpr(ref: ExprRef) extends JsValueExpr

  sealed trait KeyValuesExpr
  case class SingleKeyValueExpr(keyExpression: LabelExpr, valueExpr: JsValueExpr) extends KeyValuesExpr
  //case class MultipleKeyValuesExpr() extends KeyValuesExpr

  sealed trait LabelExpr
  case class ConstantLabelExpr(label: String) extends LabelExpr
  case class ExternalStringExpr(expr: ExprRef) extends LabelExpr

  sealed trait ExprRef
  case class IdExprRef(id: Int) extends ExprRef
}

import org.parboiled.scala._

object JsValueExprParser extends JsonParser {
  import spray.json.{ JsValueAST ⇒ AST }
  def JsValueExpr: Rule1[AST.JsValueExpr] = rule {
    ExternalJsValue | JsArrayExpr | JsObjectExpr | LiteralJsValue
  }
  def JsArrayExpr: Rule1[AST.JsArrayExpr] = rule {
    "[ " ~ zeroOrMore(JsValueExpr, separator = ", ") ~ "] " ~~> AST.JsArrayExpr
  }
  def JsObjectExpr: Rule1[AST.JsObjectExpr] = rule {
    "{ " ~ zeroOrMore(KeyValuePairExpr, separator = ", ") ~ "} " ~~> AST.JsObjectExpr
  }
  def KeyValuePairExpr: Rule1[AST.KeyValuesExpr] = rule {
    LabelExpr ~ ": " ~ JsValueExpr ~~> (AST.SingleKeyValueExpr(_, _))
  }
  def LabelExpr: Rule1[AST.LabelExpr] = rule {
    (JsonStringUnwrapped ~~> AST.ConstantLabelExpr) | (Ref ~~> AST.ExternalStringExpr)
  }
  def LiteralJsValue: Rule1[AST.LiteralJsValueExpr] = rule {
    (JsonString | JsonNumber | JsonTrue | JsonFalse | JsonNull) ~~> AST.LiteralJsValueExpr
  }
  def ExternalJsValue: Rule1[AST.ExternalJsValueExpr] = rule { Ref ~~> AST.ExternalJsValueExpr }

  def Ref: Rule1[AST.ExprRef] = rule { "###(" ~ IntegerValue ~ ")###" ~~> AST.IdExprRef }
  def IntegerValue: Rule1[Int] = rule { Integer ~> (_.toInt) }

  /**
   * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
   */
  def parseExpr(json: Array[Char]): AST.JsValueExpr = {
    val parsingResult = ReportingParseRunner(JsValueExpr).run(json)
    parsingResult.result.getOrElse {
      throw new ParsingException("Invalid JSON source:\n" + ErrorUtils.printParseErrors(parsingResult))
    }
  }
}
