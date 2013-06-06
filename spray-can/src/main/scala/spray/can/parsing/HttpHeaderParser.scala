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
import akka.util.ByteString
import spray.http.parser.HttpParser
import spray.util.SingletonException
import spray.http._
import HttpHeaders._
import CharUtils._

/**
 * Provides for time- and space-efficient parsing of an HTTP header line in an HTTP message.
 * It keeps a cache of all headers encountered in a previous request, so as to avoid reparsing and recreation of header
 * model instances.
 * For the life-time of one HTTP connection an instance of this class is owned by the connection, i.e. not shared
 * with other connections. After the connection is closed it may be used by subsequent connections.
 */
private[parsing] final class HttpHeaderParser private (val settings: ParserSettings,
                                                       warnOnIllegalHeader: ErrorInfo ⇒ Unit,
    // format: OFF

    // The core of this parser/cache is a mutable space-efficient ternary trie (prefix tree) structure, whose data are
    // split across three arrays. The tree supports node addition and update, but no deletion (i.e. we never remove
    // entries).

    // This structure keeps the main data of the trie nodes. One node is formed of a char (2 bytes) of which the
    // LSB (least significant byte) is an ASCII octet and the MSB either 0 or an index into the `nodeData` array.
    // There are three types of nodes:
    //
    // 1. Simple nodes: non-branching, non-leaf nodes (the most frequent kind of node) have an MSB of zero and exactly
    //    one logical sub-node which is the next node in the `nodes` array (i.e. the one at index n + 1).
    // 2. Branching nodes: have 2 or three children and a non-zero MSB. (MSB value - 1)*3 is the row index into the
    //    nodeData` array, which contains the branching data.
    // 3. Leaf nodes: have no sub-nodes and no character value. They merely "stop" the node chain and point to a value.
    //    The LSB of leaf nodes is zero and the (MSB value - 1) is an index into the values array.
    //
    // This design has the following consequences:
    // - Since only leaf nodes can have values the trie cannot store keys that are prefixes of other stored keys.
    // - If the trie stores n values it has less than n branching nodes (adding the first value does not create a
    //   branching node, the addition of every subsequent value creates at most one additional branching node).
    // - If the trie has n branching nodes it stores at least n * 2 and at most n * 3 values.
    //
    // The nodes array has an initial size of 512 but can grow as needed.
    private[this] var nodes: Array[Char] = new Array(512),
    private[this] var nodeCount: Int = 0,

    // This structure keeps the branching data for branching nodes in the trie.
    // It's a flattened two-dimensional array with a row consisting of the following 3 signed 16-bit integers:
    // row-index + 0: if non-zero: index into the `nodes` array for the "lesser" child of the node
    // row-index + 1: if non-zero: index into the `nodes` array for the "equal" child of the node
    // row-index + 2: if non-zero: index into the `nodes` array for the "greater" child of the node
    // The array has a fixed size of 254 rows (since we can address at most 256 - 1 values with the node MSB and
    // we always have fewer branching nodes than values).
    private[this] var branchData: Array[Short] = new Array(254 * 3),
    private[this] var branchDataCount: Int = 0,

    // The values addressed by trie leaf nodes. Since we address them via the nodes MSB and zero is reserved the trie
    // cannot hold more then 255 items, so this array has a fixed size of 255.
    private[this] var values: Array[AnyRef] = new Array(255),
    private[this] var valueCount: Int = 0,

    // signals whether we can mutate the trie data without having to copy them first
    private[this] var trieIsPrivate: Boolean = false) {

  // format: ON

  import HttpHeaderParser._
  import settings._

  /**
   * Contains the parsed header instance after a call to `parseHeaderLine`.
   */
  var resultHeader: HttpHeader = EmptyHeader

  def isEmpty = nodeCount == 0

  /**
   * Returns a copy of this parser that shares the trie data with this instance.
   */
  def copyWith(warnOnIllegalHeader: ErrorInfo ⇒ Unit) =
    new HttpHeaderParser(settings, warnOnIllegalHeader, nodes, nodeCount, branchData, branchDataCount, values, valueCount)

  /**
   * Parses a header line and returns the line start index of the subsequent line.
   * The parsed header instance is written to the `resultHeader` member.
   * If the trie still has space for this type of header (and as a whole) the parsed header is cached.
   * Throws a `NotEnoughDataException` if the given input doesn't contain enough data to fully parse the given header
   * line. Note that there must be at least one byte available after a CRLF sequence, in order to distinguish a true
   * line ending from a line fold.
   * If the header is invalid a respective `ParsingException` is thrown.
   */
  @tailrec def parseHeaderLine(input: ByteString, lineStart: Int = 0)(cursor: Int = lineStart, nodeIx: Int = 0): Int = {
    def startValueBranch(rootValueIx: Int, valueParser: HeaderValueParser) = {
      val (header, endIx) = valueParser(input, cursor, warnOnIllegalHeader)
      if (valueParser.maxValueCount > 0)
        try {
          val valueIx = newValueIndex // compute early in order to trigger OutOfTrieSpaceExceptions before any change
          unshareIfRequired()
          values(rootValueIx) = ValueBranch(rootValueIx, valueParser, branchRootNodeIx = nodeCount, valueCount = 1)
          insertRemainingCharsAsNewNodes(input, header)(cursor, endIx, valueIx)
        } catch { case OutOfTrieSpaceException ⇒ /* if we cannot insert then we simply don't */ }
      resultHeader = header
      endIx
    }
    val node = nodes(nodeIx)
    node & 0xFF match {
      case 0 ⇒ // leaf node
        val valueIx = (node >>> 8) - 1
        values(valueIx) match {
          case branch: ValueBranch            ⇒ parseHeaderValue(input, cursor, branch)()
          case valueParser: HeaderValueParser ⇒ startValueBranch(valueIx, valueParser) // no header yet of this type
          case EmptyHeader                    ⇒ resultHeader = EmptyHeader; cursor
        }
      case nodeChar ⇒
        val char = toLowerCase(byteChar(input, cursor))
        if (char == node) // fast match, advance and descend
          parseHeaderLine(input, lineStart)(cursor + 1, nodeIx + 1)
        else node >>> 8 match {
          case 0 ⇒ // header doesn't exist yet and has no model (since we have not yet seen a colon)
            parseRawHeader(input, lineStart, cursor, nodeIx)
          case msb ⇒ // branching node
            val signum = math.signum(char - nodeChar)
            branchData(rowIx(msb) + 1 + signum) match {
              case 0 ⇒ // header doesn't exist yet and has no model (otherwise we'd arrive at a value)
                parseRawHeader(input, lineStart, cursor, nodeIx)
              case subNodeIx ⇒ // descend into branch and advance on char matches (otherwise descend but don't advance)
                parseHeaderLine(input, lineStart)(cursor + 1 - abs(signum), subNodeIx)
            }
        }
    }
  }

  private def parseRawHeader(input: ByteString, lineStart: Int, cursor: Int, nodeIx: Int): Int = {
    val colonIx = scanHeaderNameAndReturnIndexOfColon(input, lineStart, lineStart + maxHeaderNameLength)(cursor)
    val headerName = asciiString(input, lineStart, colonIx)
    try {
      val valueParser = rawHeaderValueParser(headerName, maxHeaderValueLength, settings.headerValueCacheLimit(headerName))
      insert(input, valueParser)(cursor, colonIx + 1, nodeIx, colonIx)
      parseHeaderLine(input, lineStart)(cursor, nodeIx)
    } catch {
      case OutOfTrieSpaceException ⇒ // if we cannot insert we drop back to simply creating new header instances
        val (headerValue, endIx) = scanHeaderValue(input, colonIx + 1, colonIx + 1 + maxHeaderValueLength)()
        resultHeader = RawHeader(headerName, headerValue.trim)
        endIx
    }
  }

  @tailrec private def parseHeaderValue(input: ByteString, valueStart: Int, branch: ValueBranch)(cursor: Int = valueStart, nodeIx: Int = branch.branchRootNodeIx): Int = {
    def parseAndInsertHeader() = {
      val (header, endIx) = branch.parser(input, valueStart, warnOnIllegalHeader)
      if (branch.spaceLeft)
        try {
          insert(input, header)(cursor, endIx, nodeIx, colonIx = 0)
          values(branch.valueIx) = branch.withValueCountIncreased
        } catch { case OutOfTrieSpaceException ⇒ /* if we cannot insert then we simply don't */ }
      resultHeader = header
      endIx
    }
    val char = byteChar(input, cursor)
    val node = nodes(nodeIx)
    if (char == node) // fast match, descend
      parseHeaderValue(input, valueStart, branch)(cursor + 1, nodeIx + 1)
    else node >>> 8 match {
      case 0 ⇒ parseAndInsertHeader()
      case msb ⇒ node & 0xFF match {
        case 0 ⇒ // leaf node
          resultHeader = values(msb - 1).asInstanceOf[HttpHeader]
          cursor
        case nodeChar ⇒ // branching node
          val signum = math.signum(char - nodeChar)
          branchData(rowIx(msb) + 1 + signum) match {
            case 0 ⇒ parseAndInsertHeader() // header doesn't exist yet
            case subNodeIx ⇒ // descend into branch and advance on char matches (otherwise descend but don't advance)
              parseHeaderValue(input, valueStart, branch)(cursor + 1 - abs(signum), subNodeIx)
          }
      }
    }
  }

  /**
   * Inserts a value into the cache trie.
   * CAUTION: this method must only be called if
   * - the trie is not empty (use `insertRemainingCharsAsNewNodes` for inserting the very first value)
   * - the input does not contain illegal characters
   * - the input is not a prefix of an already stored value, i.e. the input must be properly terminated (CRLF or colon)
   */
  @tailrec def insert(input: ByteString, value: AnyRef)(cursor: Int = 0, endIx: Int = input.length, nodeIx: Int = 0, colonIx: Int = 0): Unit = {
    val char =
      if (cursor < colonIx) toLowerCase(input(cursor).toChar)
      else if (cursor < endIx) input(cursor).toChar
      else '\u0000'
    val node = nodes(nodeIx)
    if (char == node) insert(input, value)(cursor + 1, endIx, nodeIx + 1, colonIx) // fast match, descend into only subnode
    else {
      val nodeChar = node & 0xFF
      val signum = math.signum(char - nodeChar)
      node >>> 8 match {
        case 0 ⇒ // input doesn't exist yet in the trie, insert
          val valueIx = newValueIndex // compute early in order to trigger OutOfTrieSpaceExceptions before any change
          val rowIx = newBranchDataRowIndex
          unshareIfRequired()
          nodes(nodeIx) = nodeBits(rowIx, nodeChar)
          branchData(rowIx + 1) = (nodeIx + 1).toShort
          branchData(rowIx + 1 + signum) = nodeCount.toShort
          insertRemainingCharsAsNewNodes(input, value)(cursor, endIx, valueIx, colonIx)
        case msb ⇒
          if (nodeChar == 0) { // leaf node
            require(cursor == endIx, "Cannot insert key of which a prefix already has a value")
            values(msb - 1) = value // override existing entry
          } else {
            val branchIndex = rowIx(msb) + 1 + signum
            branchData(branchIndex) match { // branching node
              case 0 ⇒ // branch doesn't exist yet, create
                val valueIx = newValueIndex // compute early in order to trigger OutOfTrieSpaceExceptions before any change
                unshareIfRequired()
                branchData(branchIndex) = nodeCount.toShort // make the previously implicit "equals" sub node explicit
                insertRemainingCharsAsNewNodes(input, value)(cursor, endIx, valueIx, colonIx)
              case subNodeIx ⇒ // descend, but advance only on match
                insert(input, value)(cursor + 1 - abs(signum), endIx, subNodeIx, colonIx)
            }
          }
      }
    }
  }

  /**
   * Inserts a value into the cache trie as new nodes.
   * CAUTION: this method must only be called if the trie data have already been "unshared"!
   */
  @tailrec def insertRemainingCharsAsNewNodes(input: ByteString, value: AnyRef)(cursor: Int = 0, endIx: Int = input.length, valueIx: Int = newValueIndex, colonIx: Int = 0): Unit = {
    val newNodeIx = newNodeIndex
    if (cursor < endIx) {
      val c = input(cursor).toChar
      val char = if (cursor < colonIx) toLowerCase(c) else c
      nodes(newNodeIx) = char
      insertRemainingCharsAsNewNodes(input, value)(cursor + 1, endIx, valueIx, colonIx)
    } else {
      values(valueIx) = value
      nodes(newNodeIx) = ((valueIx + 1) << 8).toChar
    }
  }

  def unshareIfRequired(): Unit =
    if (!trieIsPrivate) {
      nodes = copyOf(nodes, nodes.length)
      branchData = copyOf(branchData, branchData.length)
      values = copyOf(values, values.length)
      trieIsPrivate = true
    }

  private def newNodeIndex: Int = {
    val index = nodeCount
    if (index == nodes.length) nodes = copyOf(nodes, index * 3 / 2)
    nodeCount = index + 1
    index
  }

  private def newBranchDataRowIndex: Int = {
    val index = branchDataCount
    branchDataCount = index + 3
    index
  }

  private def newValueIndex: Int = {
    val index = valueCount
    if (index < values.length) {
      valueCount = index + 1
      index
    } else throw OutOfTrieSpaceException
  }

  private def rowIx(msb: Int) = (msb - 1) * 3
  private def nodeBits(rowIx: Int, char: Int) = (((rowIx / 3 + 1) << 8) | char).toChar

  /**
   * Renders the trie structure into an ASCII representation.
   */
  def formatTrie: String = {
    def recurse(nodeIx: Int = 0): (Seq[List[String]], Int) = {
      def recurseAndPrefixLines(subNodeIx: Int, p1: String, p2: String, p3: String) = {
        val (lines, mainIx) = recurse(subNodeIx)
        val prefixedLines = lines.zipWithIndex map {
          case (line, ix) ⇒ (if (ix < mainIx) p1 else if (ix > mainIx) p3 else p2) :: line
        }
        prefixedLines -> mainIx
      }
      def branchLines(dataIx: Int, p1: String, p2: String, p3: String) = branchData(dataIx) match {
        case 0         ⇒ Seq.empty
        case subNodeIx ⇒ recurseAndPrefixLines(subNodeIx, p1, p2, p3)._1
      }
      val node = nodes(nodeIx)
      val char = escape((node & 0xFF).toChar)
      node >>> 8 match {
        case 0 ⇒ recurseAndPrefixLines(nodeIx + 1, "  ", char + "-", "  ")
        case msb ⇒ node & 0xFF match {
          case 0 ⇒ values(msb - 1) match {
            case ValueBranch(_, valueParser, branchRootNodeIx, _) ⇒
              val pad = " " * (valueParser.headerName.length + 3)
              recurseAndPrefixLines(branchRootNodeIx, pad, "(" + valueParser.headerName + ")-", pad)
            case vp: HeaderValueParser ⇒ Seq(" (" :: vp.headerName :: ")" :: Nil) -> 0
            case value: RawHeader      ⇒ Seq(" *" :: value.toString :: Nil) -> 0
            case value                 ⇒ Seq(" " :: value.toString :: Nil) -> 0
          }
          case nodeChar ⇒
            val rix = rowIx(msb)
            val preLines = branchLines(rix, "  ", "┌─", "| ")
            val postLines = branchLines(rix + 2, "| ", "└─", "  ")
            val p1 = if (preLines.nonEmpty) "| " else "  "
            val p3 = if (postLines.nonEmpty) "| " else "  "
            val (matchLines, mainLineIx) = recurseAndPrefixLines(branchData(rix + 1), p1, char + '-', p3)
            (preLines ++ matchLines ++ postLines, mainLineIx + preLines.size)
        }
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

  /**
   * Returns the number of header values stored per header type.
   */
  def contentHistogram: Map[String, Int] = {
    def build(nodeIx: Int = 0): Map[String, Int] = {
      val node = nodes(nodeIx)
      node >>> 8 match {
        case 0 ⇒ build(nodeIx + 1)
        case msb if (node & 0xFF) == 0 ⇒ values(msb - 1) match {
          case ValueBranch(_, parser, _, count) ⇒ Map(parser.headerName -> count)
          case _                                ⇒ Map.empty
        }
        case msb ⇒
          def branch(ix: Int): Map[String, Int] = if (ix > 0) build(ix) else Map.empty
          val rix = rowIx(msb)
          branch(branchData(rix + 0)) ++ branch(branchData(rix + 1)) ++ branch(branchData(rix + 2))
      }
    }
    build()
  }

  /**
   * Returns a string representation of the raw trie data.
   */
  def formatRawTrie: String = {
    def char(c: Char) = (c >> 8).toString + (if ((c & 0xFF) > 0) "/" + (c & 0xFF).toChar else "/Ω")
    "nodes: " + { nodes take nodeCount map char mkString ", " } + "\n" +
      "nodeData: " + { branchData take branchDataCount grouped 3 map (_.mkString("/")) mkString ", " } + "\n" +
      "values: " + (values take valueCount mkString ", ")
  }

  /**
   * Returns a string representation of the trie structure size.
   */
  def formatSizes: String = nodeCount + " nodes, " + { branchDataCount / 3 } + " nodeData rows, " + valueCount + " values"
}

private[parsing] object HttpHeaderParser {
  import SpecializedHeaderValueParsers._

  object EmptyHeader extends HttpHeader {
    def name = ""
    def lowercaseName = ""
    def value = ""
    def render[R <: Rendering](r: R): r.type = r
    override def toString = "EmptyHeader"
  }

  private def predefinedHeaders = Seq(
    "Accept: *",
    "Accept: */*",
    "Connection: Keep-Alive",
    "Connection: close",
    "Connection: keep-alive",
    "Content-Length: 0",
    "Cache-Control: max-age=0",
    "Cache-Control: no-cache",
    "Expect: 100-continue")

  private val defaultIllegalHeaderWarning: ErrorInfo ⇒ Unit = info ⇒ sys.error(info.formatPretty)

  def apply(settings: ParserSettings, warnOnIllegalHeader: ErrorInfo ⇒ Unit = defaultIllegalHeaderWarning,
            unprimed: Boolean = false): HttpHeaderParser = {
    val parser = new HttpHeaderParser(settings, warnOnIllegalHeader)
    if (!unprimed) {
      val valueParsers: Seq[HeaderValueParser] =
        HttpParser.headerNames.map { name ⇒
          modelledHeaderValueParser(name, HttpParser.parserRules(name.toLowerCase), parser.settings.maxHeaderValueLength,
            parser.settings.headerValueCacheLimit(name))
        }(collection.breakOut)
      def insertInGoodOrder(items: Seq[Any])(startIx: Int = 0, endIx: Int = items.size): Unit =
        if (endIx - startIx > 0) {
          val pivot = (startIx + endIx) / 2
          items(pivot) match {
            case valueParser: HeaderValueParser ⇒
              val insertName = valueParser.headerName.toLowerCase + ':'
              if (parser.isEmpty) parser.insertRemainingCharsAsNewNodes(ByteString(insertName), valueParser)()
              else parser.insert(ByteString(insertName), valueParser)()
            case header: String ⇒
              parser.parseHeaderLine(ByteString(header + "\r\nx"))()
          }
          insertInGoodOrder(items)(startIx, pivot)
          insertInGoodOrder(items)(pivot + 1, endIx)
        }
      insertInGoodOrder(valueParsers.sortBy(_.headerName))()
      insertInGoodOrder(specializedHeaderValueParsers)()
      insertInGoodOrder(predefinedHeaders.sorted)()
      parser.insert(ByteString("\r\n"), EmptyHeader)()
    }
    parser
  }

  abstract class HeaderValueParser(val headerName: String, val maxValueCount: Int) {
    def apply(input: ByteString, valueStart: Int, warnOnIllegalHeader: ErrorInfo ⇒ Unit): (HttpHeader, Int)
    override def toString: String = "HeaderValueParser[" + headerName + "]"
  }

  def modelledHeaderValueParser(headerName: String, parserRule: Rule1[HttpHeader], maxHeaderValueLength: Int,
                                maxValueCount: Int) =
    new HeaderValueParser(headerName, maxValueCount) {
      def apply(input: ByteString, valueStart: Int, warnOnIllegalHeader: ErrorInfo ⇒ Unit): (HttpHeader, Int) = {
        val (headerValue, endIx) = scanHeaderValue(input, valueStart, valueStart + maxHeaderValueLength)()
        val trimmedHeaderValue = headerValue.trim
        val header = HttpParser.parse(parserRule, trimmedHeaderValue) match {
          case Right(h) ⇒ h
          case Left(error) ⇒
            warnOnIllegalHeader(error.withSummaryPrepended("Illegal '" + headerName + "' header"))
            RawHeader(headerName, trimmedHeaderValue)
        }
        header -> endIx
      }
    }

  def rawHeaderValueParser(headerName: String, maxHeaderValueLength: Int, maxValueCount: Int) =
    new HeaderValueParser(headerName, maxValueCount) {
      def apply(input: ByteString, valueStart: Int, warnOnIllegalHeader: ErrorInfo ⇒ Unit): (HttpHeader, Int) = {
        val (headerValue, endIx) = scanHeaderValue(input, valueStart, valueStart + maxHeaderValueLength)()
        RawHeader(headerName, headerValue.trim) -> endIx
      }
    }

  @tailrec private def scanHeaderNameAndReturnIndexOfColon(input: ByteString, start: Int,
                                                           maxHeaderNameEndIx: Int)(ix: Int = start): Int =
    if (ix < maxHeaderNameEndIx)
      byteChar(input, ix) match {
        case ':'                 ⇒ ix
        case c if isTokenChar(c) ⇒ scanHeaderNameAndReturnIndexOfColon(input, start, maxHeaderNameEndIx)(ix + 1)
        case c                   ⇒ fail("Illegal character '" + escape(c) + "' in header name")
      }
    else fail("HTTP header name exceeds the configured limit of " + { maxHeaderNameEndIx - start } + " characters")

  @tailrec private def scanHeaderValue(input: ByteString, start: Int, maxHeaderValueEndIx: Int)(sb: JStringBuilder = null, ix: Int = start): (String, Int) = {
    def spaceAppended = (if (sb != null) sb else new JStringBuilder(asciiString(input, start, ix))).append(' ')
    if (ix < maxHeaderValueEndIx)
      byteChar(input, ix) match {
        case '\t' ⇒ scanHeaderValue(input, start, maxHeaderValueEndIx)(spaceAppended, ix + 1)
        case '\r' if byteChar(input, ix + 1) == '\n' ⇒
          if (isWhitespace(byteChar(input, ix + 2))) scanHeaderValue(input, start, maxHeaderValueEndIx)(spaceAppended, ix + 3)
          else (if (sb != null) sb.toString else asciiString(input, start, ix), ix + 2)
        case c if c >= ' ' ⇒ scanHeaderValue(input, start, maxHeaderValueEndIx)(if (sb != null) sb.append(c) else sb, ix + 1)
        case c             ⇒ fail("Illegal character '" + escape(c) + "' in header name")
      }
    else fail("HTTP header value exceeds the configured limit of " + { maxHeaderValueEndIx - start } + " characters")
  }

  def fail(summary: String) = throw new ParsingException(StatusCodes.BadRequest, ErrorInfo(summary))

  private object OutOfTrieSpaceException extends SingletonException

  private case class ValueBranch(valueIx: Int, parser: HeaderValueParser, branchRootNodeIx: Int, valueCount: Int) {
    def withValueCountIncreased = copy(valueCount = valueCount + 1)
    def spaceLeft = valueCount < parser.maxValueCount
  }
}
