package org.apache.spark.storage.pmof

import java.io.OutputStream
import java.nio.ByteBuffer

import io.netty.buffer.{ByteBuf, PooledByteBufAllocator}
import org.apache.spark.internal.Logging

class PmemOutputStream(persistentMemoryWriter: PersistentMemoryHandler,
                       remotePersistentMemoryPool: RemotePersistentMemoryPool,
                       numPartitions: Int,
                       blockId: String,
                       numMaps: Int
                      ) extends OutputStream with Logging {
  var set_clean = true
  var is_closed = false

  val length: Int = 1024 * 1024 * 6
  var bufferFlushedSize: Int = 0
  var bufferRemainingSize: Int = 0
  val buf: ByteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(length, length)
  val byteBuffer: ByteBuffer = buf.nioBuffer(0, length)

  override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
    byteBuffer.put(bytes, off, len)
    bufferRemainingSize += len
  }

  override def write(byte: Int): Unit = {
    byteBuffer.putInt(byte)
    bufferRemainingSize += 4
  }

  override def flush(): Unit = {
    if (bufferRemainingSize > 0) {
      if (persistentMemoryWriter != null) {
        persistentMemoryWriter.setPartition(numPartitions, blockId, byteBuffer, bufferRemainingSize, set_clean)
      } else {
        remotePersistentMemoryPool.put(blockId, byteBuffer, bufferRemainingSize)
      }
      bufferFlushedSize += bufferRemainingSize
      bufferRemainingSize = 0
    }
    if (set_clean) {
      set_clean = false
    }
  }

  def flushedSize(): Int = {
    bufferFlushedSize
  }

  def remainingSize(): Int = {
    bufferRemainingSize
  }

  def reset(): Unit = {
    bufferRemainingSize = 0
    bufferFlushedSize = 0
    byteBuffer.clear()
  }

  override def close(): Unit = synchronized {
    if (!is_closed) {
      flush()
      reset()
      buf.release()
      is_closed = true
    }
  }
}
