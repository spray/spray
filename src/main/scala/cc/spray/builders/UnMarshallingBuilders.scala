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
package builders

import marshalling._

private[spray] trait UnMarshallingBuilders extends DefaultMarshallers with DefaultUnmarshallers {
  this: FilterBuilders =>
  
  def contentAs[A :Unmarshaller](routing: A => Route): Route = {
    val filterRoute = filter1 { ctx =>
      ctx.request.content.as[A] match {
        case Right(a) => Pass(a)
        case Left(rejection) => Reject(rejection)
      }
    }
    filterRoute(routing) 
  }
  
  def optionalContentAs[A :Unmarshaller](routing: Option[A] => Route): Route = {
    val filterRoute = filter1 { ctx =>
      ctx.request.content.as[A] match {
        case Right(a) => Pass(Some(a))
        case Left(RequestEntityExpectedRejection) => Pass(None)
        case Left(rejection) => Reject(rejection)
      }
    }
    filterRoute(routing) 
  }
  
  def produces[A](routing: (A => Unit) => Route)(implicit marshaller: Marshaller[A]): Route = {
    val filterRoute = filter1 { ctx =>
      marshaller(ctx.request.isContentTypeAccepted(_)) match {
        case MarshalWith(converter) => Pass({ (a: A) => ctx.complete(converter(a)) })
        case CantMarshal(onlyTo) => Reject(UnacceptedResponseContentTypeRejection(onlyTo))
      }
    }
    filterRoute(routing)
  }
  
  def handledBy[A :Unmarshaller, B: Marshaller](f: A => B): Route = {
    contentAs[A] { a =>
      produces[B] { produce =>
        _ => produce(f(a))
      }
    }
  }
  
}