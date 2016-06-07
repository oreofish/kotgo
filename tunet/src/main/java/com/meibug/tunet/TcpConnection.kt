/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.meibug.tunet

import java.io.IOException
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

import com.meibug.tunet.util.Log.*
import com.meibug.tunet.util.Log.DEBUG
import com.meibug.tunet.util.Log.TRACE
import com.meibug.tunet.util.Log.debug
import com.meibug.tunet.util.Log.trace

/** @author Nathan Sweet @n4te.com>
 */
internal class TcpConnection(val serialization: Serialization, writeBufferSize: Int, objectBufferSize: Int) {

    var socketChannel: SocketChannel? = null
    var keepAliveMillis = 8000
    val readBuffer: ByteBuffer
    val writeBuffer: ByteBuffer
    var bufferPositionFix: Boolean = false
    var timeoutMillis = 12000
    var idleThreshold = 0.1f
    private var selectionKey: SelectionKey? = null
    @Volatile private var lastWriteTime: Long = 0
    @Volatile private var lastReadTime: Long = 0
    private var currentObjectLength: Int = 0
    private val writeLock = Object()

    init {
        writeBuffer = ByteBuffer.allocate(writeBufferSize)
        readBuffer = ByteBuffer.allocate(objectBufferSize)
        readBuffer.flip()
    }

    @Throws(IOException::class)
    fun accept(selector: Selector, socketChannel: SocketChannel): SelectionKey? {
        writeBuffer.clear()
        readBuffer.clear()
        readBuffer.flip()
        currentObjectLength = 0
        try {
            this.socketChannel = socketChannel
            socketChannel.configureBlocking(false)
            val socket = socketChannel.socket()
            socket.tcpNoDelay = true

            selectionKey = socketChannel.register(selector, SelectionKey.OP_READ)

            if (DEBUG) {
                debug("kryonet", "Port " + socketChannel.socket().localPort + "/TCP connected to: "
                        + socketChannel.socket().remoteSocketAddress)
            }

            lastReadTime = System.currentTimeMillis()
            lastWriteTime = System.currentTimeMillis()

            return selectionKey
        } catch (ex: IOException) {
            close()
            throw ex
        }

    }

    @Throws(IOException::class)
    fun connect(selector: Selector, remoteAddress: SocketAddress, timeout: Int) {
        close()
        writeBuffer.clear()
        readBuffer.clear()
        readBuffer.flip()
        currentObjectLength = 0
        try {
            val socketChannel = selector.provider().openSocketChannel()
            val socket = socketChannel.socket()
            socket.tcpNoDelay = true
            // socket.setTrafficClass(IPTOS_LOWDELAY);
            socket.connect(remoteAddress, timeout) // Connect using blocking mode for simplicity.
            socketChannel.configureBlocking(false)
            this.socketChannel = socketChannel

            selectionKey = socketChannel.register(selector, SelectionKey.OP_READ)
            selectionKey!!.attach(this)

            if (DEBUG) {
                debug("kryonet", "Port " + socketChannel.socket().localPort + "/TCP connected to: "
                        + socketChannel.socket().remoteSocketAddress)
            }

            lastReadTime = System.currentTimeMillis()
            lastWriteTime = System.currentTimeMillis()
        } catch (ex: IOException) {
            close()
            val ioEx = IOException("Unable to connect to: " + remoteAddress, ex)
            throw ioEx
        }

    }

    @Throws(IOException::class)
    fun readObject(connection: Connection): Any? {
        val socketChannel = this.socketChannel ?: throw SocketException("Connection is closed.")

        if (currentObjectLength == 0) {
            // Read the length of the next object from the socket.
            val lengthLength = serialization.lengthLength
            if (readBuffer.remaining() < lengthLength) {
                readBuffer.compact()
                val bytesRead = socketChannel.read(readBuffer)
                readBuffer.flip()
                if (bytesRead == -1) throw SocketException("Connection is closed.")
                lastReadTime = System.currentTimeMillis()

                if (readBuffer.remaining() < lengthLength) return null
            }
            currentObjectLength = serialization.readLength(readBuffer)

            if (currentObjectLength <= 0) throw KryoNetException("Invalid object length: " + currentObjectLength)
            if (currentObjectLength > readBuffer.capacity())
                throw KryoNetException("Unable to read object larger than read buffer: " + currentObjectLength)
        }

        val length = currentObjectLength
        if (readBuffer.remaining() < length) {
            // Fill the tcpInputStream.
            readBuffer.compact()
            val bytesRead = socketChannel.read(readBuffer)
            readBuffer.flip()
            if (bytesRead == -1) throw SocketException("Connection is closed.")
            lastReadTime = System.currentTimeMillis()

            if (readBuffer.remaining() < length) return null
        }
        currentObjectLength = 0

        val startPosition = readBuffer.position()
        val oldLimit = readBuffer.limit()
        readBuffer.limit(startPosition + length)
        val `object`: Any
        try {
            `object` = serialization.read(connection, readBuffer)
        } catch (ex: Exception) {
            throw KryoNetException("Error during deserialization.", ex)
        }

        readBuffer.limit(oldLimit)
        if (readBuffer.position() - startPosition != length)
            throw KryoNetException("Incorrect number of bytes (" + (startPosition + length - readBuffer.position())
                    + " remaining) used to deserialize object: " + `object`)

        return `object`
    }

    @Throws(IOException::class)
    fun writeOperation() {
        synchronized (writeLock) {
            if (writeToSocket()) {
                // Write successful, clear OP_WRITE.
                selectionKey!!.interestOps(SelectionKey.OP_READ)
            }
            lastWriteTime = System.currentTimeMillis()
        }
    }

    @Throws(IOException::class)
    private fun writeToSocket(): Boolean {
        val socketChannel = this.socketChannel ?: throw SocketException("Connection is closed.")

        val buffer = writeBuffer
        buffer.flip()
        while (buffer.hasRemaining()) {
            if (bufferPositionFix) {
                buffer.compact()
                buffer.flip()
            }
            if (socketChannel.write(buffer) == 0) break
        }
        buffer.compact()

        return buffer.position() == 0
    }

    /** This method is thread safe.  */
    @Throws(IOException::class)
    fun send(connection: Connection, `object`: Any): Int {
        val socketChannel = this.socketChannel ?: throw SocketException("Connection is closed.")
        synchronized (writeLock) {
            // Leave room for length.
            val start = writeBuffer.position()
            val lengthLength = serialization.lengthLength
            writeBuffer.position(writeBuffer.position() + lengthLength)

            // Write data.
            try {
                serialization.write(connection, writeBuffer, `object`)
            } catch (ex: KryoNetException) {
                throw KryoNetException("Error serializing object of type: " + `object`.javaClass.name, ex)
            }

            val end = writeBuffer.position()

            // Write data length.
            writeBuffer.position(start)
            serialization.writeLength(writeBuffer, end - lengthLength - start)
            writeBuffer.position(end)

            // Write to socket if no data was queued.
            if (start == 0 && !writeToSocket()) {
                // A partial write, set OP_WRITE to be notified when more writing can occur.
                selectionKey!!.interestOps(SelectionKey.OP_READ or SelectionKey.OP_WRITE)
            } else {
                // Full write, wake up selector so idle event will be fired.
                selectionKey!!.selector().wakeup()
            }

            if (DEBUG || TRACE) {
                val percentage = writeBuffer.position() / writeBuffer.capacity().toFloat()
                if (DEBUG && percentage > 0.75f)
                    debug("kryonet", connection.toString() + " TCP write buffer is approaching capacity: " + percentage + "%")
                else if (TRACE && percentage > 0.25f)
                    trace("kryonet", connection.toString() + " TCP write buffer utilization: " + percentage + "%")
            }

            lastWriteTime = System.currentTimeMillis()
            return end - start
        }
    }

    fun close() {
        try {
            if (socketChannel != null) {
                socketChannel!!.close()
                socketChannel = null
                if (selectionKey != null) selectionKey!!.selector().wakeup()
            }
        } catch (ex: IOException) {
            if (DEBUG) debug("kryonet", "Unable to close TCP connection.", ex)
        }

    }

    fun needsKeepAlive(time: Long): Boolean {
        return socketChannel != null && keepAliveMillis > 0 && time - lastWriteTime > keepAliveMillis
    }

    fun isTimedOut(time: Long): Boolean {
        return socketChannel != null && timeoutMillis > 0 && time - lastReadTime > timeoutMillis
    }

    companion object {
        private val IPTOS_LOWDELAY = 0x10
    }
}
