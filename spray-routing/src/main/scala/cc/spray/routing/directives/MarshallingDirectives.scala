/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.routing
package directives

import shapeless._
import cc.spray.httpx.marshalling._
import cc.spray.httpx.unmarshalling._
import cc.spray.http._


trait MarshallingDirectives {
  import BasicDirectives._
  import MiscDirectives._

  /**
   * Unmarshalls the requests entity to the given type passes it to its inner Route.
   * If there is a problem with unmarshalling the request is rejected with the [[cc.spray.routing.Rejection]]
   * produced by the unmarshaller.
   */
  def entity[T](um: Unmarshaller[T]): Directive[T :: HNil] = filter { ctx =>
    ctx.request.entity.as(um) match {
      case Right(value) => Pass(value :: HNil)
      case Left(ContentExpected) => Reject(RequestEntityExpectedRejection)
      case Left(UnsupportedContentType(supported)) => Reject(UnsupportedRequestContentTypeRejection(supported))
      case Left(MalformedContent(error)) => Reject(MalformedRequestContentRejection(error))
    }
  } & cancelAllRejections(ofTypes(RequestEntityExpectedRejection.getClass, classOf[UnsupportedRequestContentTypeRejection]))

  /**
   * Returns the in-scope Unmarshaller for the given type.
   */
  def as[T](implicit um: Unmarshaller[T]) = um

  /**
   * Uses the marshaller for the given type to produce a completion function that is passed to its inner route.
   * You can use it do decouple marshaller resolution from request completion.
   */
  def produce[T](mm: Marshaller[T], status: StatusCode = StatusCodes.OK,
                 headers: List[HttpHeader] = Nil): Directive[(T => Unit) :: HNil] = filter { ctx =>
    mm(ctx.request.acceptableContentType) match {
      case Right(marshalling) => Pass((marshalling(_:T, ctx.marshallingContext(status, headers))) :: HNil)
      case Left(onlyTo) => Reject(UnacceptedResponseContentTypeRejection(onlyTo))
    }
  } & cancelAllRejections(ofType[UnacceptedResponseContentTypeRejection])

  /**
   * Returns the in-scope Marshaller for the given type.
   */
  def instanceOf[T](implicit m: Marshaller[T]) = m

  /**
   * Completes the request using the given function. The input to the function is produced with the in-scope
   * entity unmarshaller and the result value of the function is marshalled with the in-scope marshaller.
   */
  def handleWith[A, B](f: A => B)(implicit um: Unmarshaller[A], m: Marshaller[B]): Route =
    entity(um) { a => RouteDirectives.complete(f(a)) }

}

object MarshallingDirectives extends MarshallingDirectives