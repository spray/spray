package cc.spray
package examples.stopwatch

import http._
import typeconversion._
import MediaTypes._
import collection.Map

trait StopWatchMarshallers extends DefaultMarshallers {
  this: StopWatchService =>

  /**
   * Marshaller for a whole map of StopWatches
   */
  implicit object StopWatchesMarshaller extends SimpleMarshaller[Map[Int, StopWatch]] {
    val canMarshalTo = ContentType(`text/html`) :: Nil

    def marshal(value: Map[Int, StopWatch], contentType: ContentType) = contentType match {
      case x@ ContentType(`text/html`, _) => NodeSeqMarshaller.marshal(marshalToHtml(value), x)  
      case _ => throw new IllegalArgumentException
    }
    
    def marshalToHtml(value: Map[Int, StopWatch]) = {
      <html>
        <body>
          <h1>Stopwatches</h1>
          {
            if (value.isEmpty) {
              <p>No watches defined</p>
            } else {
              for ((id, watch) <- value) yield {
                val watchUri = "/watch/" + id              
                <p>Watch {id}:
                {
                  List("view", "delete", "start", "stop", "clear").map { action =>
                    val path = if (action == "view") "" else "/" + action + "?method=post" 
                    <span> <a href={watchUri + path}>{action}</a></span>
                  }                
                }
                </p>
              }
            }
          }
          <p><a href="/watches?method=post">Create new watch</a></p>
        </body>
      </html>
    }
  }
  
  /**
   * Marshaller for a single StopWatch
   */
  implicit object StopWatchMarshaller extends SimpleMarshaller[StopWatch] {
    val canMarshalTo = ContentType(`text/html`) :: Nil

    def marshal(value: StopWatch, contentType: ContentType) = contentType match {
      case x@ ContentType(`text/html`, _) => NodeSeqMarshaller.marshal(marshalToHtml(value), x)    
      case _ => throw new IllegalArgumentException
    }
    
    def marshalToHtml(value: StopWatch) = {
      <html>
        <body>
          <table>
            <tr><td>State:</td><td>{value.state}</td></tr>
            <tr><td>Time:</td><td>{value.time / 1000} sec</td></tr>
          </table>
        </body>
      </html>
    }
  }
  
}