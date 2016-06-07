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
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

import com.meibug.tunet.util.Log.*
import com.meibug.tunet.util.Log.DEBUG
import com.meibug.tunet.util.Log.debug

/** @author Nathan Sweet @n4te.com>
 */
internal class UdpConnection(private val serialization: Serialization, bufferSize: Int) {
    var connectedAddress: InetSocketAddress? = null
    var datagramChannel: DatagramChannel? = null
    var keepAliveMillis = 19000
    val readBuffer: ByteBuffer
    val writeBuffer: ByteBuffer
    private var selectionKey: SelectionKey? = null
    private val writeLock = Object()
    private var lastCommunicationTime: Long = 0

    init {
        readBuffer = ByteBuffer.allocate(bufferSize)
        writeBuffer = ByteBuffer.allocateDirect(bufferSize)
    }

    @Throws(IOException::class)
    fun bind(selector: Selector, localPort: InetSocketAddress) {
        close()
        readBuffer.clear()
        writeBuffer.clear()
        try {
            datagramChannel = selector.provider().openDatagramChannel()
            datagramChannel!!.socket().bind(localPort)
            datagramChannel!!.configureBlocking(false)
            selectionKey = datagramChannel!!.register(selector, SelectionKey.OP_READ)

            lastCommunicationTime = System.currentTimeMillis()
        } catch (ex: IOException) {
            close()
            throw ex
        }

    }

    @Throws(IOException::class)
    fun connect(selector: Selector, remoteAddress: InetSocketAddress) {
        close()
        readBuffer.clear()
        writeBuffer.clear()
        try {
            datagramChannel = selector.provider().openDatagramChannel()
            datagramChannel!!.socket().bind(null)
            datagramChannel!!.socket().connect(remoteAddress)
            datagramChannel!!.configureBlocking(false)

            selectionKey = datagramChannel!!.register(selector, SelectionKey.OP_READ)

            lastCommunicationTime = System.currentTimeMillis()

            connectedAddress = remoteAddress
        } catch (ex: IOException) {
            close()
            val ioEx = IOException("Unable to connect to: " + remoteAddress, ex)
            throw ioEx
        }

    }

    @Throws(IOException::class)
    fun readFromAddress(): InetSocketAddress {
        val datagramChannel = this.datagramChannel ?: throw SocketException("Connection is closed.")
        lastCommunicationTime = System.currentTimeMillis()
        return datagramChannel.receive(readBuffer) as InetSocketAddress
    }

    fun readObject(connection: Connection?): Any {
        readBuffer.flip()
        try {
            try {
                val `object` = serialization.read(connection, readBuffer)
                if (readBuffer.hasRemaining())
                    throw KryoNetException("Incorrect number of bytes (" + readBuffer.remaining()
                            + " remaining) used to deserialize object: " + `object`)
                return `object`
            } catch (ex: Exception) {
                throw KryoNetException("Error during deserialization.", ex)
            }

        } finally {
            readBuffer.clear()
        }
    }

    /** This method is thread safe.  */
    @Throws(IOException::class)
    fun send(connection: Connection, `object`: Any, address: SocketAddress): Int {
        val datagramChannel = this.datagramChannel ?: throw SocketException("Connection is closed.")
        synchronized (writeLock) {
            try {
                try {
                    serialization.write(connection, writeBuffer, `object`)
                } catch (ex: Exception) {
                    throw KryoNetException("Error serializing object of type: " + `object`.javaClass.name, ex)
                }

                writeBuffer.flip()
                val length = writeBuffer.limit()
                datagramChannel.send(writeBuffer, address)

                lastCommunicationTime = System.currentTimeMillis()

                val wasFullWrite = !writeBuffer.hasRemaining()
                return if (wasFullWrite) length else -1
            } finally {
                writeBuffer.clear()
            }
        }
    }

    fun close() {
        connectedAddress = null
        try {
            if (datagramChannel != null) {
                datagramChannel!!.close()
                datagramChannel = null
                if (selectionKey != null) selectionKey!!.selector().wakeup()
            }
        } catch (ex: IOException) {
            if (DEBUG) debug("kryonet", "Unable to close UDP connection.", ex)
        }

    }

    fun needsKeepAlive(time: Long): Boolean {
        return connectedAddress != null && keepAliveMillis > 0 && time - lastCommunicationTime > keepAliveMillis
    }
}
