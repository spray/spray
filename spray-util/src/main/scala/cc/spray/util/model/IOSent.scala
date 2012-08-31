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

package cc.spray.util.model

// generally available marker traits
//
// they are defined here in spray-util because spray-util is the only module that is
// available to all other spray modules (except spray-http) and we can this way mark
// spray-io events with these marker traits and identify the events in spray-httpx
// (for example) without introducing a dependency on spray-io
trait IOSent
trait IOClosed

// default implementations
object DefaultIOSent extends IOSent
object DefaultIOClosed extends IOClosed