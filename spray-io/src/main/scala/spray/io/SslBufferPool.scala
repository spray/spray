/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.io

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import annotation.tailrec


/**
 * A ByteBuffer pool reduces the number of ByteBuffer allocations in the SslTlsSupport.
 * The reason why SslTlsSupport requires a buffer pool is because the
 * current SSLEngine implementation always requires a 17KiB buffer for
 * every 'wrap' and 'unwrap' operation.  In most cases, the actual size of the
 * required buffer is much smaller than that, and therefore allocating a 17KiB
 * buffer for every 'wrap' and 'unwrap' operation wastes a lot of memory
 * bandwidth, resulting in application performance degradation.
 *
 * This implementation is very loosely based on the one from Netty.
 */
trait SslBufferPool[T] {

  // we are using Nettys default values:
  // 16665 + 1024 (room for compressed data) + 1024 (for OpenJDK compatibility)
  val BufferSize = 16665 + 2048

  private val Unlocked = 0
  private val Locked = 1

  private[this] val state = new AtomicInteger(Unlocked)
  @volatile private[this] var pool: List[T] = Nil

  /**
   * Returns the size of the current buffer pool.
   * CAUTION: this method has complexity O(n), with n being the size of the pool
   */
  def size: Int = pool.size

  def allocate(capacity: Int): T
  def refurbish(buffer: T): Unit

  @tailrec
  final def acquire(): T = {
    if (state.compareAndSet(Unlocked, Locked)) {
      try pool match {
        case Nil => allocate(BufferSize) // we have no more buffer available, so create a new one
        case buf :: tail =>
          pool = tail
          buf
      } finally state.set(Unlocked)
    } else acquire() // spin while locked
  }

  @tailrec
  final def release(buf: T) {
    if (state.compareAndSet(Unlocked, Locked)) {
      refurbish(buf) // ensure that we never have dirty buffers in the pool
      pool = buf :: pool
      state.set(Unlocked)
    } else release(buf) // spin while locked
  }
}

object SslBufferPool extends SslBufferPool[ByteBuffer] {
  def allocate(capacity: Int): ByteBuffer =
    ByteBuffer.allocate(capacity)

  def refurbish(buffer: ByteBuffer) {
    buffer.clear()
  }
}
