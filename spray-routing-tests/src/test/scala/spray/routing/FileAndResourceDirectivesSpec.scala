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

package spray.routing

import java.io.File
import com.typesafe.config.ConfigFactory
import org.parboiled.common.FileUtils
import scala.util.Properties
import spray.http._
import MediaTypes._
import HttpHeaders._
import HttpCharsets._

class FileAndResourceDirectivesSpec extends RoutingSpec {

  override def testConfigSource =
    """spray.routing {
      |  file-chunking-threshold-size = 16
      |  file-chunking-chunk-size = 8
      |}""".stripMargin

  "getFromFile" should {
    "reject non-GET requests" in {
      Put() ~> getFromFile("some") ~> check { handled must beFalse }
    }
    "reject requests to non-existing files" in {
      Get() ~> getFromFile("nonExistentFile") ~> check { handled must beFalse }
    }
    "reject requests to directories" in {
      Get() ~> getFromFile(Properties.javaHome) ~> check { handled must beFalse }
    }
    "return the file content with the MediaType matching the file extension" in {
      val file = File.createTempFile("sprayTest", ".PDF")
      FileUtils.writeAllText("This is PDF", file)
      Get() ~> getFromFile(file.getPath) ~> check {
        mediaType === `application/pdf`
        definedCharset === None
        body.asString === "This is PDF"
        headers === List(`Last-Modified`(DateTime(file.lastModified)))
      }
      file.delete
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("sprayTest", null)
      FileUtils.writeAllText("Some content", file)
      Get() ~> getFromFile(file) ~> check {
        mediaType === `application/octet-stream`
        body.asString === "Some content"
      }
      file.delete
    }
    "return a chunked response for files larger than the configured file-chunking-threshold-size" in {
      val file = File.createTempFile("sprayTest2", ".xml")
      FileUtils.writeAllText("<this could be XML if it were formatted correctly>", file)
      Get() ~> getFromFile(file) ~> check {
        mediaType === `text/xml`
        definedCharset === Some(`UTF-8`)
        body.asString === "<this co"
        headers === List(`Last-Modified`(DateTime(file.lastModified)))
        chunks.map(_.bodyAsString).mkString("|") === "uld be X|ML if it| were fo|rmatted |correctl|y>"
      }
      file.delete
    }
  }

  "getFromResource" should {
    "reject non-GET requests" in {
      Put() ~> getFromResource("some") ~> check { handled must beFalse }
    }
    "reject requests to non-existing resources" in {
      Get() ~> getFromResource("nonExistingResource") ~> check { handled must beFalse }
    }
    "return the resource content with the MediaType matching the file extension" in {
      val route = getFromResource("sample.html")

      def runCheck =
        Get() ~> route ~> check {
          mediaType === `text/html`
          body.asString === "<p>Lorem ipsum!</p>"
          headers must have {
            case `Last-Modified`(dt) ⇒ DateTime(2011, 7, 1) < dt && dt.clicks < System.currentTimeMillis()
          }
        }

      runCheck
      runCheck // additional test to check that no internal state is kept
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      Get() ~> getFromResource("sample.xyz") ~> check {
        mediaType === `application/octet-stream`
        body.asString === "XyZ"
      }
    }
  }

  "getFromResourceDirectory" should {
    "reject requests to non-existing resources" in {
      Get("not/found") ~> getFromResourceDirectory("subDirectory") ~> check { handled must beFalse }
    }
    "return the resource content with the MediaType matching the file extension" in {
      val verify = check {
        mediaType === `application/pdf`
        body.asString === "123\n"
      }
      "example 1" in { Get("empty.pdf") ~> getFromResourceDirectory("subDirectory") ~> verify }
      "example 2" in { Get("empty.pdf") ~> getFromResourceDirectory("subDirectory/") ~> verify }
      "example 3" in { Get("subDirectory/empty.pdf") ~> getFromResourceDirectory("") ~> verify }
    }
    "reject requests to directory resources" in {
      Get() ~> getFromResourceDirectory("subDirectory") ~> check { handled must beFalse }
    }
  }

  "listDirectoryContents" should {
    val base = new File(getClass.getClassLoader.getResource("").toURI).getPath
    new File(base, "subDirectory/emptySub").mkdir()
    def eraseDateTime(s: String) = s.replaceAll("""\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d""", "xxxx-xx-xx xx:xx:xx")
    implicit val settings = RoutingSettings.default.copy(renderVanityFooter = false)

    "properly render a simple directory" in {
      Get() ~> listDirectoryContents(base + "/someDir") ~> check {
        eraseDateTime(entityAs[String]) ===
          """<html>
            |<head><title>Index of /</title></head>
            |<body>
            |<h1>Index of /</h1>
            |<hr>
            |<pre>
            |<a href="/sub/">sub/</a>             xxxx-xx-xx xx:xx:xx
            |<a href="/fileA.txt">fileA.txt</a>        xxxx-xx-xx xx:xx:xx            3  B
            |<a href="/fileB.xml">fileB.xml</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |""".stripMargin
      }
    }
    "properly render a sub directory" in {
      Get("/sub/") ~> listDirectoryContents(base + "/someDir") ~> check {
        eraseDateTime(entityAs[String]) ===
          """<html>
            |<head><title>Index of /sub/</title></head>
            |<body>
            |<h1>Index of /sub/</h1>
            |<hr>
            |<pre>
            |<a href="/">../</a>
            |<a href="/sub/file.html">file.html</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |""".stripMargin
      }
    }
    "properly render the union of several directories" in {
      Get() ~> listDirectoryContents(base + "/someDir", base + "/subDirectory") ~> check {
        eraseDateTime(entityAs[String]) ===
          """<html>
            |<head><title>Index of /</title></head>
            |<body>
            |<h1>Index of /</h1>
            |<hr>
            |<pre>
            |<a href="/emptySub/">emptySub/</a>        xxxx-xx-xx xx:xx:xx
            |<a href="/sub/">sub/</a>             xxxx-xx-xx xx:xx:xx
            |<a href="/empty.pdf">empty.pdf</a>        xxxx-xx-xx xx:xx:xx            4  B
            |<a href="/fileA.txt">fileA.txt</a>        xxxx-xx-xx xx:xx:xx            3  B
            |<a href="/fileB.xml">fileB.xml</a>        xxxx-xx-xx xx:xx:xx            0  B
            |</pre>
            |<hr>
            |</body>
            |</html>
            |""".stripMargin
      }
    }
    "properly render an empty sub directory with vanity footer" in {
      val settings = 0 // shadow implicit
      Get("/emptySub/") ~> listDirectoryContents(base + "/subDirectory") ~> check {
        eraseDateTime(entityAs[String]) ===
          """<html>
            |<head><title>Index of /emptySub/</title></head>
            |<body>
            |<h1>Index of /emptySub/</h1>
            |<hr>
            |<pre>
            |<a href="/">../</a>
            |</pre>
            |<hr>
            |<div style="width:100%;text-align:right;color:gray">
            |<small>rendered by <a href="http://spray.io">spray</a> on xxxx-xx-xx xx:xx:xx</small>
            |</div>
            |</body>
            |</html>
            |""".stripMargin
      }
    }
    "properly render an empty top-level directory" in {
      Get() ~> listDirectoryContents(base + "/subDirectory/emptySub") ~> check {
        eraseDateTime(entityAs[String]) ===
          """<html>
            |<head><title>Index of /</title></head>
            |<body>
            |<h1>Index of /</h1>
            |<hr>
            |<pre>
            |(no files)
            |</pre>
            |<hr>
            |</body>
            |</html>
            |""".stripMargin
      }
    }
    "reject requests to file resources" in {
      Get() ~> listDirectoryContents(base + "subDirectory/empty.pdf") ~> check { handled must beFalse }
    }
  }
}