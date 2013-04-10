/*
 * Copyright (C) 2011-2013 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.can.parsing

import java.util.Arrays.copyOf
import java.lang.{ StringBuilder ⇒ JStringBuilder }
import org.parboiled.scala.rules.Rule1
import scala.annotation.tailrec
import scala.collection.immutable.NumericRange
import akka.util.CompactByteString
import spray.http.parser.HttpParser
import spray.http._
import HttpHeaders._

/**
 * Provides for time- and space-efficient parsing of an HTTP header line in an HTTP message.
 * It keeps a cache of all headers encountered in a previous request, so as to avoid reparsing and recreation of header
 * model instances.
 * For the life-time of one HTTP connection an instance of this class is owned by the connection, i.e. not shared
 * with other connections. After the connection is closed it may be used by subsequent connections.
 */
private[parsing] final class HttpHeaderParser private (settings: ParserSettings, warnOnIllegalHeader: ErrorInfo ⇒ Unit,
    // format: OFF

    // The core of this parser/cache is a mutable space-efficient ternary trie (prefix tree) structure, whose data are
    // split across three arrays. The tree supports node addition, but no deletion (i.e. we never remove entries).
    // The trie structure itself (i.e. without cached header instances) has a memory footprint of about 5 KB on a
    // 64-bit JVM (ca. 1-2 KB + 256*3*2 + 256*8). However, since connections share the trie structure if they don't
    // encounter new headers the per-connection overhead on a long-running server is much lower.

    // This structure keeps the main data of the trie nodes. One node is formed of a char (2 bytes) of which the
    // LSB (least significant byte) is an ASCII octet and the MSB either 0 or an index into the `nodeData` array.
    // If the MSB of a node is zero the node is a non-branching non-leaf node (the most frequent kind of node in
    // the trie). Such nodes have only one logical sub-node which is the next node in the `nodes` array (i.e. the
    // one at index n + 1). If a node is a branching node (i.e. with more than one child) xor a leaf node
    // (i.e. with an attached value) its MSB is non-zero. Its (value-1)*3 is then used as the column index into the
    // `nodeData` array.
    // This array has an initial size of 512 but can grow as needed.
    private[this] var nodes: Array[Char] = new Array(512),

    // This structure keeps the additional node data required for branching and leaf nodes in the trie.
    // It's a flattened two-dimensional array with a row consisting of the following 3 signed 16-bit integers:
    // col-index + 0: if non-zero: index into the `nodes` array for the "lesser" child of the node
    // col-index + 1: if non-zero: index into the `nodes` array for the "equal" child of the node
    // col-index + 2: if non-zero: index into the `nodes` array for the "greater" child of the node
    // If the (col-index + 1) integer is negative the node is a leaf-node. In this case its value ^ 0xFFFFFFFF is
    // the index into the `values` array and the two other integers (col-index + 0/2) are not used.
    // The array has a fixed size of 256 rows.
    private[this] var nodeData: Array[Short] = new Array(256 * 3),

    // The values for trie nodes that are endpoints.
    // This array has a fixed size of 256.
    private[this] var values: Array[AnyRef] = new Array(256)) {

  // format: ON

  import HttpHeaderParser._

  private[this] var trieIsShared = true
  private[this] var nodeCount = 0
  private[this] var nodeDataCount = 0
  private[this] var valueCount = 0

  var resultHeader: HttpHeader = EmptyHeader

  def isEmpty = nodeCount == 0

  def copyWith(warnOnIllegalHeader: ErrorInfo ⇒ Unit) =
    new HttpHeaderParser(settings, warnOnIllegalHeader, nodes, nodeData, values)

  @tailrec def parseHeaderLine(input: CompactByteString, lineStart: Int = 0)(cursor: Int = lineStart, nodeIx: Int = 0): Int = {
    def insertRawHeaderParser(): Unit = {
      val colonIx = scanHeaderNameAndReturnIndexOfColon(input, cursor)
      val headerName = asciiString(input, lineStart, colonIx)
      val valueParser = rawHeaderValueParser(headerName)
      insert(input, valueParser)(cursor, colonIx + 1, nodeIx, colonIx)
    }
    val char = toLowerCase(byteChar(input, cursor))
    val node = nodes(nodeIx)
    if (char == node)
      parseHeaderLine(input, lineStart)(cursor + 1, nodeIx + 1) // fast match, advance and descend
    else node >>> 8 match {
      case 0 ⇒ // header doesn't exist yet and has no model (since we have not yet seen a colon)
        insertRawHeaderParser()
        parseHeaderLine(input, lineStart)(cursor, nodeIx)

      case msb ⇒ // we have either a value or branching data for this node
        val colIx = (msb - 1) * 3
        math.signum(char - (node & 0xFF)) match {
          case 0 ⇒ // character match
            val nData = nodeData(colIx + 1)
            if (char == ':') { // the first colon always has a value attached (and no node above it)
              values(nData ^ 0xFFFFFFFF) match {
                case (valueParser: HeaderValueParser, nextNodeIx: Int) ⇒
                  parseHeaderValue(input, cursor + 1, nextNodeIx, valueParser)()
                case valueParser: HeaderValueParser ⇒ // no header yet of this type
                  val (header, endIx) = valueParser(input, cursor + 1, warnOnIllegalHeader)
                  values(nData ^ 0xFFFFFFFF) = valueParser -> nodeCount
                  insertRemainingCharsAsNewNodes(input, header)(cursor + 1, endIx)
                  resultHeader = header
                  endIx
              }
            } else parseHeaderLine(input, lineStart)(cursor + 1, nData) // advance and descend

          case signum ⇒ // character mismatch, check whether we can branch
            val nextNodeIx = nodeData(colIx + 1 + signum) match {
              case 0         ⇒ insertRawHeaderParser(); nodeIx // header doesn't exist yet and has no model
              case subNodeIx ⇒ subNodeIx // descend into branch (but don't advance)
            }
            parseHeaderLine(input, lineStart)(cursor, nextNodeIx)
        }
    }
  }

  @tailrec private def parseHeaderValue(input: CompactByteString, valueStart: Int, nodeIx: Int,
                                        valueParser: HeaderValueParser)(cursor: Int = valueStart): Int = {
    def parseAndInsertHeader() = {
      val (header, endIx) = valueParser(input, valueStart, warnOnIllegalHeader)
      insert(input, header)(cursor, endIx, nodeIx, colonIx = 0)
      resultHeader = header
      endIx
    }
    val char = byteChar(input, cursor)
    val node = nodes(nodeIx)
    if (char == node) // fast match, descend
      parseHeaderValue(input, valueStart, nodeIx + 1, valueParser)(cursor + 1)
    else node >>> 8 match {
      case 0 ⇒ parseAndInsertHeader()
      case msb ⇒
        val colIx = (msb - 1) * 3
        math.signum(char - (node & 0xFF)) match {
          case 0 ⇒ // character match
            nodeData(colIx + 1) match {
              case x if x < 0 ⇒
                resultHeader = values(x ^ 0xFFFFFFFF).asInstanceOf[HttpHeader]
                cursor + 1
              case subNodeIx ⇒ // advance and descend
                parseHeaderValue(input, valueStart, subNodeIx, valueParser)(cursor + 1)
            }
          case signum ⇒ // character mismatch, check whether we can branch
            nodeData(colIx + 1 + signum) match {
              case 0 ⇒ parseAndInsertHeader()
              case subNodeIx ⇒ // descend into branch (but don't advance)
                parseHeaderValue(input, valueStart, subNodeIx, valueParser)(cursor)
            }
        }
    }
  }

  // NOTE: only valid input must be inserted into the trie (i.e. it must not contain illegal characters and be properly
  // terminated by a CRLF) and the trie must not be empty!
  // Use `insertRemainingCharsAsNewNodes` for inserting the very first entry.
  def insert(input: CompactByteString, value: AnyRef)(startIx: Int = 0, endIx: Int = input.length, nodeIx: Int = 0, colonIx: Int = 0): Unit = {
    @tailrec def recurse(cursor: Int, nodeIx: Int): Unit = {
      if (cursor >= endIx)
        throw new IllegalArgumentException("Cannot insert value for prefix of existing trie entry")
      val c = input(cursor).toChar
      val char = if (cursor < colonIx) toLowerCase(c) else c
      val node = nodes(nodeIx)
      if (char == node) recurse(cursor + 1, nodeIx + 1) // fast match, descend into only subnode
      else {
        val signum = math.signum(char - (node & 0xFF))
        node >>> 8 match {
          case 0 ⇒ // input doesn't exist yet in the trie, insert
            val colIx = newNodeDataColumnIndex
            nodes(nodeIx) = nodeChar(colIx, node & 0xFF)
            nodeData(colIx + 1) = (nodeIx + 1).toShort
            nodeData(colIx + 1 + signum) = nodeCount.toShort
            insertRemainingCharsAsNewNodes(input, value)(cursor, endIx, colonIx)

          case msb ⇒
            val colIx = (msb - 1) * 3
            nodeData(colIx + 1 + signum) match {
              case 0 ⇒ // branch doesn't exist yet, create
                nodeData(colIx + 1 + signum) = nodeCount.toShort
                insertRemainingCharsAsNewNodes(input, value)(cursor, endIx, colonIx)
              case x if x < 0 ⇒
                require(cursor == endIx - 1, "Cannot insert key of which a prefix already has a value")
                values(x ^ 0xFFFFFFFF) = value // override existing entry
              case nextNodeIx ⇒ recurse(cursor + 1 - abs(signum), nextNodeIx) // descend, but advance only on match
            }
        }
      }
    }
    if (trieIsShared) {
      nodes = copyOf(nodes, nodes.length)
      nodeData = copyOf(nodeData, nodeData.length)
      values = copyOf(values, values.length)
      trieIsShared = false
    }
    recurse(startIx, nodeIx)
  }

  @tailrec def insertRemainingCharsAsNewNodes(input: CompactByteString, value: AnyRef)(cursor: Int = 0, endIx: Int = input.length, colonIx: Int = 0): Unit = {
    val newNodeIx = newNodeIndex
    val c = input(cursor).toChar
    val char = if (cursor < colonIx) toLowerCase(c) else c
    if (cursor == endIx - 1) {
      val valueIx = newValueIndex
      values(valueIx) = value
      val colIx = newNodeDataColumnIndex
      nodeData(colIx + 1) = (valueIx ^ 0xFFFFFFFF).toShort
      nodes(newNodeIx) = nodeChar(colIx, char)
    } else {
      nodes(newNodeIx) = char
      insertRemainingCharsAsNewNodes(input, value)(cursor + 1, endIx, colonIx)
    }
  }

  private def newNodeIndex: Int = {
    if (nodeCount == nodes.length) nodes = copyOf(nodes, nodes.length * 2)
    val index = nodeCount
    nodeCount += 1
    index
  }

  private def newNodeDataColumnIndex: Int = {
    val index = nodeDataCount
    nodeDataCount += 3
    index
  }

  private def newValueIndex: Int = {
    val index = valueCount
    valueCount += 1
    index
  }

  private def nodeChar(colIx: Int, char: Int) = (((colIx / 3 + 1) << 8) | char).toChar

  def inspect: String = {
    def recurse(nodeIx: Int = 0): (Seq[List[String]], Int) = {
      def recurseAndPrefixLines(subNodeIx: Int, p1: String, p2: String, p3: String) = {
        val (lines, mainIx) = recurse(subNodeIx)
        val prefixedLines = lines.zipWithIndex map {
          case (line, ix) ⇒ (if (ix < mainIx) p1 else if (ix > mainIx) p3 else p2) :: line
        }
        prefixedLines -> mainIx
      }
      def branchLines(dataIx: Int, p1: String, p2: String, p3: String) = nodeData(dataIx) match {
        case 0         ⇒ Seq.empty
        case subNodeIx ⇒ recurseAndPrefixLines(subNodeIx, p1, p2, p3)._1
      }
      val node = nodes(nodeIx)
      val char = escape((node & 0xFF).toChar)
      node >>> 8 match {
        case 0 ⇒ recurseAndPrefixLines(nodeIx + 1, "  ", char + "-", "  ")
        case msb ⇒
          val colIx = (msb - 1) * 3
          val preLines = branchLines(colIx, "  ", "┌─", "| ")
          val postLines = branchLines(colIx + 2, "| ", "└─", "  ")
          val p1 = if (preLines.nonEmpty) "| " else "  "
          val p3 = if (postLines.nonEmpty) "| " else "  "
          val (matchLines, mainLineIx) = nodeData(colIx + 1) match {
            case x if x < 0 ⇒ values(x ^ 0xFFFFFFFF) match {
              case (valueParser: HeaderValueParser, nextNodeIx: Int) ⇒
                val pad = " " * (valueParser.headerName.length + 2)
                recurseAndPrefixLines(nextNodeIx, p1 + pad, char + '(' + valueParser.headerName + ")-", p3 + pad)
              case vp: HeaderValueParser ⇒ Seq(char :: " (" :: vp.headerName :: ")" :: Nil) -> 0
              case value: RawHeader      ⇒ Seq(char :: " *" :: value.toString :: Nil) -> 0
              case value                 ⇒ Seq(char :: " " :: value.toString :: Nil) -> 0
            }
            case subNodeIx ⇒ recurseAndPrefixLines(subNodeIx, p1, char + "-", p3)
          }
          (preLines ++ matchLines ++ postLines, mainLineIx + preLines.size)
      }
    }
    val sb = new JStringBuilder()
    val (lines, mainLineIx) = recurse()
    lines.zipWithIndex foreach {
      case (line, ix) ⇒
        sb.append(if (ix == mainLineIx) '-' else ' ')
        line foreach (s ⇒ sb.append(s))
        sb.append('\n')
    }
    sb.toString
  }

  def inspectRaw: String =
    s"nodes: ${nodes take nodeCount map (c ⇒ (c >> 8) + "/'" + (c & 0xFF).toChar) mkString ", "}\n" +
      s"nodeData: ${nodeData take nodeDataCount grouped 3 map { case Array(a, b, c) ⇒ s"$a/$b/$c" } mkString ", "}\n" +
      s"values: ${values take valueCount mkString ", "}"

  def inspectSizes: String = s"$nodeCount nodes, ${nodeDataCount / 3} nodeData rows, $valueCount values"
}

private[parsing] object HttpHeaderParser extends SpecializedHeaderValueParsers {
  object EmptyHeader extends HttpHeader {
    def name = ""
    def lowercaseName = ""
    def value = ""
    override def toString = "EmptyHeader"
  }

  private def predefinedHeaders = Seq(
    "Accept: *",
    "Accept: */*",
    "Connection: Keep-Alive",
    "Connection: close",
    "Connection: keep-alive",
    "Cache-Control: max-age=0")

  def apply(settings: ParserSettings, warnOnIllegalHeader: ErrorInfo ⇒ Unit) =
    new HttpHeaderParser(settings, warnOnIllegalHeader)

  def prime(parser: HttpHeaderParser): HttpHeaderParser = {
    require(parser.isEmpty)
    val valueParsers: Seq[HeaderValueParser] =
      HttpParser.headerNames.map(n ⇒ modelledHeaderValueParser(n, HttpParser.parserRules(n.toLowerCase)))(collection.breakOut)
    def insertInGoodOrder(items: Seq[Any])(startIx: Int = 0, endIx: Int = items.size): Unit =
      if (endIx - startIx > 0) {
        val pivot = (startIx + endIx) / 2
        items(pivot) match {
          case valueParser: HeaderValueParser ⇒
            val insertName = valueParser.headerName.toLowerCase + ':'
            if (parser.isEmpty) parser.insertRemainingCharsAsNewNodes(CompactByteString(insertName), valueParser)()
            else parser.insert(CompactByteString(insertName), valueParser)()
          case header: String ⇒
            parser.parseHeaderLine(CompactByteString(header + "\r\nx"))()
        }
        insertInGoodOrder(items)(startIx, pivot)
        insertInGoodOrder(items)(pivot + 1, endIx)
      }
    insertInGoodOrder(valueParsers.sortBy(_.headerName))()
    // insertInGoodOrder(specializedHeaderValueParsers)
    insertInGoodOrder(predefinedHeaders.sorted)()
    parser.insert(CompactByteString("\r\n"), EmptyHeader)()
    parser
  }

  abstract class HeaderValueParser(val headerName: String) {
    def apply(input: CompactByteString, valueStart: Int, warnOnIllegalHeader: ErrorInfo ⇒ Unit): (HttpHeader, Int)
    override def toString: String = s"HeaderValueParser[$headerName]"
  }

  def modelledHeaderValueParser(headerName: String, parserRule: Rule1[HttpHeader]) =
    new HeaderValueParser(headerName) {
      def apply(input: CompactByteString, valueStart: Int, warnOnIllegalHeader: ErrorInfo ⇒ Unit): (HttpHeader, Int) = {
        val (headerValue, endIx) = scanHeaderValue(input, valueStart)()
        val trimmedHeaderValue = headerValue.trim
        val header = HttpParser.parse(parserRule, trimmedHeaderValue) match {
          case Right(h) ⇒ h
          case Left(error) ⇒
            warnOnIllegalHeader(error)
            RawHeader(headerName, trimmedHeaderValue)
        }
        header -> endIx
      }
    }

  def rawHeaderValueParser(headerName: String) =
    new HeaderValueParser(headerName) {
      def apply(input: CompactByteString, valueStart: Int, warnOnIllegalHeader: ErrorInfo ⇒ Unit): (HttpHeader, Int) = {
        val (headerValue, endIx) = scanHeaderValue(input, valueStart)()
        RawHeader(headerName, headerValue.trim) -> endIx
      }
    }

  @tailrec private def scanHeaderNameAndReturnIndexOfColon(input: CompactByteString, ix: Int): Int =
    byteChar(input, ix) match {
      case ':'               ⇒ ix
      case c if is(c, TOKEN) ⇒ scanHeaderNameAndReturnIndexOfColon(input, ix + 1)
      case c                 ⇒ fail(s"Illegal character '${escape(c)}' in header name")
    }

  @tailrec private def scanHeaderValue(input: CompactByteString, start: Int)(sb: JStringBuilder = null, ix: Int = start): (String, Int) = {
    def spaceAppended = (if (sb != null) sb else new JStringBuilder(asciiString(input, start, ix))).append(' ')
    byteChar(input, ix) match {
      case '\t' ⇒ scanHeaderValue(input, start)(spaceAppended, ix + 1)
      case '\r' if byteChar(input, ix + 1) == '\n' ⇒
        if (is(byteChar(input, ix + 2), WSP)) scanHeaderValue(input, start)(spaceAppended, ix + 3)
        else (if (sb != null) sb.toString else asciiString(input, start, ix), ix + 2)
      case c if c >= ' ' ⇒ scanHeaderValue(input, start)(if (sb != null) sb.append(c) else sb, ix + 1)
      case c             ⇒ fail(s"Illegal character '${escape(c)}' in header name")
    }
  }

  private def fail(summary: String) = throw new ParsingException(StatusCodes.BadRequest, ErrorInfo(summary))

  // compile time constants
  private final val LOWER_ALPHA = 0x01
  private final val UPPER_ALPHA = 0x02
  private final val ALPHA = LOWER_ALPHA | UPPER_ALPHA
  private final val DIGIT = 0x04
  private final val TOKEN_SPECIALS = 0x08
  private final val TOKEN = ALPHA | TOKEN_SPECIALS
  private final val WSP = 0x10

  private[this] val props = new Array[Byte](128)

  private def is(c: Int, mask: Int): Boolean = (props(index(c)) & mask) != 0
  private def index(c: Int): Int = c & ((c - 127) >> 31) // branchless for `if (c <= 127) c else 0`
  private def mark(mask: Int, chars: Char*): Unit = chars.foreach(c ⇒ props(index(c)) = (props(index(c)) | mask).toByte)
  private def mark(mask: Int, range: NumericRange[Char]): Unit = mark(mask, range.toSeq: _*)

  mark(LOWER_ALPHA, 'a' to 'z')
  mark(UPPER_ALPHA, 'A' to 'Z')
  mark(DIGIT, '0' to '9')
  mark(TOKEN_SPECIALS, '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~')
  mark(WSP, ' ', '\t')

  private def toLowerCase(c: Char): Char = if (is(c, UPPER_ALPHA)) (c + 0x20).toChar else c
  private def abs(i: Int): Int = { val j = i >> 31; (i ^ j) - j }
  private def escape(c: Char): String = c match {
    case '\t'                           ⇒ "\\t"
    case '\r'                           ⇒ "\\r"
    case '\n'                           ⇒ "\\n"
    case x if Character.isISOControl(x) ⇒ "\\u%04x" format c.toInt
    case x                              ⇒ x.toString
  }
}