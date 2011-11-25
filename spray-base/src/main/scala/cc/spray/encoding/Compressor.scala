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
package encoding

import java.io.ByteArrayOutputStream

trait Compressor {
  def compress(buffer: Array[Byte], output: ByteArrayOutputStream): ByteArrayOutputStream
  def flush(output: ByteArrayOutputStream): ByteArrayOutputStream
  def finish(output: ByteArrayOutputStream): ByteArrayOutputStream
}

trait Decompressor {
  def decompress(buffer: Array[Byte], offset: Int, output: ResettableByteArrayOutputStream): Int
}



