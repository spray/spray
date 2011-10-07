/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package directives

import marshalling._

private[spray] trait MarshallingDirectives extends DefaultMarshallers with DefaultUnmarshallers {
  this: BasicDirectives =>

  /**
   * Returns a Route that unmarshalls the requests content using the given unmarshaller and
   * passes it as an argument to the inner Route building function.
   * If the unmarshaller cannot unmarshal the request content the request is rejected with the [[cc.spray.Rejection]]
   * produced by the unmarshaller.
   */
  def content[A](unmarshaller: Unmarshaller[A]) = filter1 { ctx =>
    ctx.request.content.as(unmarshaller) match {
      case Right(a) => Pass.withTransform(a) {
        _.cancelRejections {
          case RequestEntityExpectedRejection => true
          case _: UnsupportedContentType => true
          case _ => false
        }
      }
      case Left(problem) => Reject {
        problem match {
          case ContentExpected => RequestEntityExpectedRejection
          case UnsupportedContentType(supported) => UnsupportedRequestContentTypeRejection(supported)
          case MalformedContent(error) => MalformedRequestContentRejection(error)
        }
      }
    }
  }

  /**
   * Returns the in-scope Unmarshaller for the given type.
   */
  def as[A :Unmarshaller] = unmarshaller[A]

  /**
   * Returns a Route that uses the given marshaller to produce a completion function that is
   * passed to the inner route building function. You can use it do decouple marshaller resolution from the call
   * site of the RequestContexts {{complete}} function.
   */
  def produce[A](marshaller: Marshaller[A]) = filter1 { ctx =>
    marshaller(ctx.request.acceptableContentType) match {
      case MarshalWith(converter) => Pass.withTransform[A => Unit](a => ctx.complete(converter(a))) {
        _.cancelRejectionsOfType[UnacceptedResponseContentTypeRejection]
      }
      case CantMarshal(onlyTo) => Reject(UnacceptedResponseContentTypeRejection(onlyTo))
    }
  }

  /**
   * Returns the in-scope Marshaller for the given type.
   */
  def instanceOf[A :Marshaller] = marshaller[A]

  /**
   * Returns a Route that completes the request using the given function. The input to the function is produced with
   * the in-scope unmarshaller and the result value of the function is marshalled with the in-scope marshaller.
   */
  def handleWith[A :Unmarshaller, B: Marshaller](f: A => B): Route = {
    (content(as[A]) & produce(instanceOf[B])) { (a, p) =>
      _ => p(f(a))
    }
  }
  
}