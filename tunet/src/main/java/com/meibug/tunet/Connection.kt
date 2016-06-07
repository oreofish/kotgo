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

import com.meibug.tunet.FrameworkMessage.Ping
import com.meibug.tunet.util.Log

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.nio.channels.SocketChannel

import com.meibug.tunet.util.Log.*
import com.meibug.tunet.util.Log.DEBUG
import com.meibug.tunet.util.Log.ERROR
import com.meibug.tunet.util.Log.INFO
import com.meibug.tunet.util.Log.TRACE
import com.meibug.tunet.util.Log.debug
import com.meibug.tunet.util.Log.error
import com.meibug.tunet.util.Log.info
import com.meibug.tunet.util.Log.trace

// BOZO - Layer to handle handshake state.

/** Represents a TCP and optionally a UDP connection between a [Client] and a [Server]. If either underlying connection
 * is closed or errors, both connections are closed.
 * @author Nathan Sweet @n4te.com>
 */
open class Connection(serialization: Serialization, writeBufferSize: Int, objectBufferSize: Int) {
    /** Returns the server assigned ID. Will return -1 if this connection has never been connected or the last assigned ID if this
     * connection has been disconnected.  */
    var id = -1
        internal set
    private var name: String? = null
    /** Returns the local [Client] or [Server] to which this connection belongs.  */
    var endPoint: EndPoint? = null
        internal set
    internal var tcp: TcpConnection
    internal var udp: UdpConnection? = null
    internal var udpRemoteAddress: InetSocketAddress? = null
    private var listeners = arrayOf<Listener?>()
    private val listenerLock = Object()
    private var lastPingID: Int = 0
    private var lastPingSendTime: Long = 0
    /** Returns the last calculated TCP return trip time, or -1 if [.updateReturnTripTime] has never been called or the
     * [FrameworkMessage.Ping] response has not yet been received.  */
    var returnTripTime: Int = 0
        private set
    /** Returns true if this connection is connected to the remote end. Note that a connection can become disconnected at any time.  */
    @Volatile var isConnected: Boolean = false
        internal set(isConnected) {
            if (isConnected && name == null) name = "Connection " + id
        }
    /**
     * Returns the last protocol error that occured on the connection.

     * @return The last protocol error or null if none error occured.
     */
    @Volatile var lastProtocolError: KryoNetException? = null
        internal set

    init {
        tcp = TcpConnection(serialization, writeBufferSize, objectBufferSize)
    }

    /** Sends the object over the network using TCP.
     * @return The number of bytes sent.
     * *
     * @see Kryo.register
     */
    fun sendTCP(obj: Any?): Int {
        if (obj == null) throw IllegalArgumentException("object cannot be null.")
        try {
            val length = tcp.send(this, obj)
            if (length == 0) {
                if (TRACE) Log.trace("kryonet", toString() + " TCP had nothing to send.")
            } else if (DEBUG) {
                val objectString = if (obj == null) "null" else obj.javaClass.simpleName
                if (obj !is FrameworkMessage) {
                    debug("kryonet", toString() + " sent TCP: " + objectString + " (" + length + ")")
                } else if (TRACE) {
                    trace("kryonet", toString() + " sent TCP: " + objectString + " (" + length + ")")
                }
            }
            return length
        } catch (ex: IOException) {
            if (DEBUG) debug("kryonet", "Unable to send TCP with connection: " + this, ex)
            close()
            return 0
        } catch (ex: KryoNetException) {
            if (ERROR) error("kryonet", "Unable to send TCP with connection: " + this, ex)
            close()
            return 0
        }

    }

    /** Sends the object over the network using UDP.
     * @return The number of bytes sent.
     * *
     * @see Kryo.register
     * @throws IllegalStateException if this connection was not opened with both TCP and UDP.
     */
    fun sendUDP(obj: Any?): Int {
        if (obj == null) throw IllegalArgumentException("object cannot be null.")
        var address: SocketAddress? = udpRemoteAddress
        if (address == null && udp != null) address = udp!!.connectedAddress
        if (address == null && isConnected) throw IllegalStateException("Connection is not connected via UDP.")

        try {
            if (address == null) throw SocketException("Connection is closed.")

            val length = udp!!.send(this, obj, address)
            if (length == 0) {
                if (TRACE) trace("kryonet", toString() + " UDP had nothing to send.")
            } else if (DEBUG) {
                if (length != -1) {
                    val objectString = if (obj == null) "null" else obj.javaClass.simpleName
                    if (obj !is FrameworkMessage) {
                        debug("kryonet", toString() + " sent UDP: " + objectString + " (" + length + ")")
                    } else if (TRACE) {
                        trace("kryonet", toString() + " sent UDP: " + objectString + " (" + length + ")")
                    }
                } else
                    debug("kryonet", toString() + " was unable to send, UDP socket buffer full.")
            }
            return length
        } catch (ex: IOException) {
            if (DEBUG) debug("kryonet", "Unable to send UDP with connection: " + this, ex)
            close()
            return 0
        } catch (ex: KryoNetException) {
            if (ERROR) error("kryonet", "Unable to send UDP with connection: " + this, ex)
            close()
            return 0
        }

    }

    open fun close() {
        val wasConnected = isConnected
        isConnected = false
        tcp.close()
        if (udp != null && udp!!.connectedAddress != null) udp!!.close()
        if (wasConnected) {
            notifyDisconnected()
            if (INFO) info("kryonet", toString() + " disconnected.")
        }
        isConnected = false
    }

    /** Requests the connection to communicate with the remote computer to determine a new value for the
     * [return trip time][.getReturnTripTime]. When the connection receives a [FrameworkMessage.Ping] object with
     * [isReply][Ping.isReply] set to true, the new return trip time is available.  */
    fun updateReturnTripTime() {
        val ping = Ping()
        ping.id = lastPingID++
        lastPingSendTime = System.currentTimeMillis()
        sendTCP(ping)
    }

    /** An empty object will be sent if the TCP connection has not sent an object within the specified milliseconds. Periodically
     * sending a keep alive ensures that an abnormal close is detected in a reasonable amount of time (see [.setTimeout]
     * ). Also, some network hardware will close a TCP connection that ceases to transmit for a period of time (typically 1+
     * minutes). Set to zero to disable. Defaults to 8000.  */
    fun setKeepAliveTCP(keepAliveMillis: Int) {
        tcp.keepAliveMillis = keepAliveMillis
    }

    /** If the specified amount of time passes without receiving an object over TCP, the connection is considered closed. When a TCP
     * socket is closed normally, the remote end is notified immediately and this timeout is not needed. However, if a socket is
     * closed abnormally (eg, power loss), KryoNet uses this timeout to detect the problem. The timeout should be set higher than
     * the [TCP keep alive][.setKeepAliveTCP] for the remote end of the connection. The keep alive ensures that the remote
     * end of the connection will be constantly sending objects, and setting the timeout higher than the keep alive allows for
     * network latency. Set to zero to disable. Defaults to 12000.  */
    fun setTimeout(timeoutMillis: Int) {
        tcp.timeoutMillis = timeoutMillis
    }

    /** If the listener already exists, it is not added again.  */
    open fun addListener(listener: Listener?) {
        if (listener == null) throw IllegalArgumentException("listener cannot be null.")
        synchronized (listenerLock) {
            val listeners = this.listeners
            val n = listeners.size
            for (i in 0..n - 1)
                if (listener === listeners[i]) return
            val newListeners = arrayOfNulls<Listener>(n + 1)
            newListeners[0] = listener
            System.arraycopy(listeners, 0, newListeners, 1, n)
            this.listeners = newListeners
        }
        if (TRACE) trace("kryonet", "Connection listener added: " + listener.javaClass.name)
    }

    open fun removeListener(listener: Listener?) {
        if (listener == null) throw IllegalArgumentException("listener cannot be null.")
        synchronized (listenerLock) {
            val listeners = this.listeners
            val n = listeners.size
            if (n == 0) return
            val newListeners = arrayOfNulls<Listener>(n - 1)
            var i = 0
            var ii = 0
            while (i < n) {
                val copyListener = listeners[i]
                if (listener === copyListener) {
                    i++
                    continue
                }
                if (ii == n - 1) return
                newListeners[ii++] = copyListener
                i++
            }
            this.listeners = newListeners
        }
        if (TRACE) trace("kryonet", "Connection listener removed: " + listener.javaClass.name)
    }

    internal fun notifyConnected() {
        if (INFO) {
            val socketChannel = tcp.socketChannel
            if (socketChannel != null) {
                val socket = tcp.socketChannel!!.socket()
                if (socket != null) {
                    val remoteSocketAddress = socket.remoteSocketAddress as InetSocketAddress
                    if (remoteSocketAddress != null) info("kryonet", toString() + " connected: " + remoteSocketAddress.address)
                }
            }
        }
        val listeners = this.listeners
        var i = 0
        val n = listeners.size
        while (i < n) {
            listeners[i]?.connected(this)
            i++
        }
    }

    internal fun notifyDisconnected() {
        val listeners = this.listeners
        var i = 0
        val n = listeners.size
        while (i < n) {
            listeners[i]?.disconnected(this)
            i++
        }
    }

    internal fun notifyIdle() {
        val listeners = this.listeners
        var i = 0
        val n = listeners.size
        while (i < n) {
            listeners[i]?.idle(this)
            if (!isIdle) break
            i++
        }
    }

    internal fun notifyReceived(obj: Any) {
        if (obj is Ping) {
            if (obj.isReply) {
                if (obj.id == lastPingID - 1) {
                    returnTripTime = (System.currentTimeMillis() - lastPingSendTime).toInt()
                    if (TRACE) trace("kryonet", toString() + " return trip time: " + returnTripTime)
                }
            } else {
                obj.isReply = true
                sendTCP(obj)
            }
        }
        val listeners = this.listeners
        var i = 0
        val n = listeners.size
        while (i < n) {
            listeners[i]?.received(this, obj)
            i++
        }
    }

    /** Returns the IP address and port of the remote end of the TCP connection, or null if this connection is not connected.  */
    val remoteAddressTCP: InetSocketAddress?
        get() {
            val socketChannel = tcp.socketChannel
            if (socketChannel != null) {
                val socket = tcp.socketChannel!!.socket()
                if (socket != null) {
                    return socket.remoteSocketAddress as InetSocketAddress
                }
            }
            return null
        }

    /** Returns the IP address and port of the remote end of the UDP connection, or null if this connection is not connected.  */
    val remoteAddressUDP: InetSocketAddress?
        get() {
            val connectedAddress = udp!!.connectedAddress
            return connectedAddress ?: udpRemoteAddress
        }

    /** Workaround for broken NIO networking on Android 1.6. If true, the underlying NIO buffer is always copied to the beginning of
     * the buffer before being given to the SocketChannel for sending. The Harmony SocketChannel implementation in Android 1.6
     * ignores the buffer position, always copying from the beginning of the buffer. This is fixed in Android 2.0+.  */
    fun setBufferPositionFix(bufferPositionFix: Boolean) {
        tcp.bufferPositionFix = bufferPositionFix
    }

    /** Sets the friendly name of this connection. This is returned by [.toString] and is useful for providing application
     * specific identifying information in the logging. May be null for the default name of "Connection X", where X is the
     * connection ID.  */
    fun setName(name: String) {
        this.name = name
    }

    /** Returns the number of bytes that are waiting to be written to the TCP socket, if any.  */
    val tcpWriteBufferSize: Int
        get() = tcp.writeBuffer.position()

    /** @see .setIdleThreshold
     */
    val isIdle: Boolean
        get() = tcp.writeBuffer.position() / tcp.writeBuffer.capacity().toFloat() < tcp.idleThreshold

    /** If the percent of the TCP write buffer that is filled is less than the specified threshold,
     * [Listener.idle] will be called for each network thread update. Default is 0.1.  */
    fun setIdleThreshold(idleThreshold: Float) {
        tcp.idleThreshold = idleThreshold
    }

    override fun toString(): String {
        return name ?: "Connection " + id
    }
}
