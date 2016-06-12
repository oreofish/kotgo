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

import com.meibug.tunet.FrameworkMessage.DiscoverHost
import com.meibug.tunet.FrameworkMessage.RegisterTCP
import com.meibug.tunet.FrameworkMessage.RegisterUDP

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.security.AccessControlException
import java.util.ArrayList
import java.util.Collections

import com.meibug.tunet.util.Log.DEBUG
import com.meibug.tunet.util.Log.ERROR
import com.meibug.tunet.util.Log.INFO
import com.meibug.tunet.util.Log.TRACE
import com.meibug.tunet.util.Log.debug
import com.meibug.tunet.util.Log.error
import com.meibug.tunet.util.Log.info
import com.meibug.tunet.util.Log.trace

/** Represents a TCP and optionally a UDP connection to a [Server].
 * @author Nathan Sweet @n4te.com>
 */
class Client @JvmOverloads constructor(writeBufferSize: Int = 8192, objectBufferSize: Int = 2048, override var serialization: Serialization = JsonSerialization()) : Connection(serialization, writeBufferSize, objectBufferSize), EndPoint {
    lateinit private var selector: Selector
    private var emptySelects: Int = 0
    @Volatile private var tcpRegistered: Boolean = false
    @Volatile private var udpRegistered: Boolean = false
    private val tcpRegistrationLock = Object()
    private val udpRegistrationLock = Object()
    @Volatile private var shutdown: Boolean = false
    private val updateLock = Object()
    override var updateThread: Thread? = null
    private var connectTimeout: Int = 0
    private var connectHost: InetAddress? = null
    private var connectTcpPort: Int = 0
    private var connectUdpPort: Int = 0
    private var isClosed: Boolean = false
    var discoveryHandler = ClientDiscoveryHandler.DEFAULT

    init {
        endPoint = this

        try {
            selector = Selector.open()
        } catch (ex: IOException) {
            throw RuntimeException("Error opening selector.", ex)
        }
    }

    /** Opens a TCP and UDP client.
     * @see .connect
     */
    @Throws(IOException::class)
    fun connect(timeout: Int, host: String, tcpPort: Int, udpPort: Int = -1) {
        connect(timeout, InetAddress.getByName(host), tcpPort, udpPort)
    }

    /** Opens a TCP and UDP client. Blocks until the connection is complete or the timeout is reached.
     *
     *
     * Because the framework must perform some minimal communication before the connection is considered successful,
     * [.update] must be called on a separate thread during the connection process.
     * @throws IllegalStateException if called from the connection's update thread.
     * *
     * @throws IOException if the client could not be opened or connecting times out.
     */
    @Throws(IOException::class)
    @JvmOverloads fun connect(timeout: Int, host: InetAddress?, tcpPort: Int, udpPort: Int = -1) {
        if (host == null) throw IllegalArgumentException("host cannot be null.")
        if (Thread.currentThread() === updateThread)
            throw IllegalStateException("Cannot connect on the connection's update thread.")
        this.connectTimeout = timeout
        this.connectHost = host
        this.connectTcpPort = tcpPort
        this.connectUdpPort = udpPort
        close()

        if (INFO) {
            when (udpPort) {
                -1 -> info("kryonet", "Connecting: $host:$tcpPort/$udpPort")
                else -> info("kryonet", "Connecting: $host:$tcpPort")
            }
        }

        id = -1
        try {
            if (udpPort != -1) udp = UdpConnection(serialization!!, tcp.readBuffer.capacity())

            var endTime: Long = 0
            synchronized (updateLock) {
                tcpRegistered = false
                selector.wakeup()
                endTime = System.currentTimeMillis() + timeout
                tcp.connect(selector, InetSocketAddress(host, tcpPort), 5000)
            }

            // Wait for RegisterTCP.
            synchronized (tcpRegistrationLock) {
                while (!tcpRegistered && System.currentTimeMillis() < endTime) {
                    try {
                        tcpRegistrationLock.wait(100)
                    } catch (ignored: InterruptedException) {
                    }

                }
                if (!tcpRegistered) {
                    throw SocketTimeoutException("Connected, but timed out during TCP registration.\n" + "Note: Client#update must be called in a separate thread during connect.")
                }
            }

            if (udpPort != -1) {
                val udpAddress = InetSocketAddress(host, udpPort)
                synchronized (updateLock) {
                    udpRegistered = false
                    selector.wakeup()
                    udp?.connect(selector, udpAddress)
                }

                // Wait for RegisterUDP reply.
                synchronized (udpRegistrationLock) {
                    while (!udpRegistered && System.currentTimeMillis() < endTime) {
                        val registerUDP = RegisterUDP()
                        registerUDP.connectionID = id
                        udp?.send(this, registerUDP, udpAddress)
                        try {
                            udpRegistrationLock.wait(100)
                        } catch (ignored: InterruptedException) {
                        }

                    }
                    if (!udpRegistered)
                        throw SocketTimeoutException("Connected, but timed out during UDP registration: $host:$udpPort")
                }
            }
        } catch (ex: IOException) {
            close()
            throw ex
        }

    }

    /** Calls [connect][.connect] with the specified timeout and the other values last passed to
     * connect.
     * @throws IllegalStateException if connect has never been called.
     */
    @Throws(IOException::class)
    @JvmOverloads fun reconnect(timeout: Int = connectTimeout) {
        if (connectHost == null) throw IllegalStateException("This client has never been connected.")
        connect(timeout, connectHost, connectTcpPort, connectUdpPort)
    }

    /** Reads or writes any pending data for this client. Multiple threads should not call this method at the same time.
     * @param timeout Wait for up to the specified milliseconds for data to be ready to process. May be zero to return immediately
     * *           if there is no data to process.
     */
    @Throws(IOException::class)
    override fun update(timeout: Int) {
        updateThread = Thread.currentThread()
        synchronized (updateLock) { // Blocks to avoid a select while the selector is used to bind the server connection.
        }
        val startTime = System.currentTimeMillis()
        var select = 0
        if (timeout > 0) {
            select = selector.select(timeout.toLong())
        } else {
            select = selector.selectNow()
        }
        if (select == 0) {
            emptySelects++
            if (emptySelects == 100) {
                emptySelects = 0
                // NIO freaks and returns immediately with 0 sometimes, so try to keep from hogging the CPU.
                val elapsedTime = System.currentTimeMillis() - startTime
                try {
                    if (elapsedTime < 25) Thread.sleep(25 - elapsedTime)
                } catch (ex: InterruptedException) {
                }

            }
        } else {
            emptySelects = 0
            isClosed = false
            val keys = selector.selectedKeys()
            synchronized (keys) {
                val iter = keys.iterator()
                while (iter.hasNext()) {
                    keepAlive()
                    val selectionKey = iter.next()
                    iter.remove()
                    try {
                        val ops = selectionKey.readyOps()
                        if (ops and SelectionKey.OP_READ == SelectionKey.OP_READ) {
                            if (selectionKey.attachment() === tcp) {
                                while (true) {
                                    val obj = tcp.readObject(this) ?: break
                                    if (!tcpRegistered) {
                                        if (obj is RegisterTCP) {
                                            id = obj.connectionID
                                            synchronized (tcpRegistrationLock) {
                                                tcpRegistered = true
                                                tcpRegistrationLock.notifyAll()
                                                if (TRACE) trace("kryonet", toString() + " received TCP: RegisterTCP")
                                                if (udp == null) isConnected = true
                                            }
                                            if (udp == null) notifyConnected()
                                        }
                                        continue
                                    }
                                    if (udp != null && !udpRegistered) {
                                        if (obj is RegisterUDP) {
                                            synchronized (udpRegistrationLock) {
                                                udpRegistered = true
                                                udpRegistrationLock.notifyAll()
                                                if (TRACE) trace("kryonet", toString() + " received UDP: RegisterUDP")
                                                if (DEBUG) {
                                                    debug("kryonet", "Port " + udp?.datagramChannel!!.socket().localPort
                                                            + "/UDP connected to: " + udp?.connectedAddress)
                                                }
                                                isConnected = true
                                            }
                                            notifyConnected()
                                        }
                                        continue
                                    }
                                    if (!isConnected) continue
                                    if (DEBUG) {
                                        val objectString = if (obj == null) "null" else obj.javaClass.simpleName
                                        if (obj !is FrameworkMessage) {
                                            debug("kryonet", toString() + " received TCP: " + objectString)
                                        } else if (TRACE) {
                                            trace("kryonet", toString() + " received TCP: " + objectString)
                                        }
                                    }
                                    notifyReceived(obj)
                                }
                            } else {
                                if (udp?.readFromAddress() == null) continue
                                val obj = udp?.readObject(this) ?: continue
                                if (DEBUG) {
                                    val objectString = if (obj == null) "null" else obj.javaClass.simpleName
                                    debug("kryonet", toString() + " received UDP: " + objectString)
                                }
                                notifyReceived(obj)
                            }
                        }
                        if (ops and SelectionKey.OP_WRITE == SelectionKey.OP_WRITE) tcp.writeOperation()
                    } catch (ignored: CancelledKeyException) {
                        // Connection is closed.
                    }

                }
            }
        }
        if (isConnected) {
            val time = System.currentTimeMillis()
            if (tcp.isTimedOut(time)) {
                if (DEBUG) debug("kryonet", toString() + " timed out.")
                close()
            } else
                keepAlive()
            if (isIdle) notifyIdle()
        }
    }

    internal fun keepAlive() {
        if (!isConnected) return
        val time = System.currentTimeMillis()
        if (tcp.needsKeepAlive(time)) sendTCP(FrameworkMessage.keepAlive)
        if (udpRegistered && udp?.needsKeepAlive(time)!!) sendUDP(FrameworkMessage.keepAlive) // FIXME
    }

    override fun run() {
        if (TRACE) trace("kryonet", "Client thread started.")
        shutdown = false
        while (!shutdown) {
            try {
                update(250)
            } catch (ex: IOException) {
                if (TRACE) {
                    if (isConnected)
                        trace("kryonet", "Unable to update connection: " + this, ex)
                    else
                        trace("kryonet", "Unable to update connection.", ex)
                } else if (DEBUG) {
                    if (isConnected)
                        debug("kryonet", toString() + " update: " + ex.message)
                    else
                        debug("kryonet", "Unable to update connection: " + ex.message)
                }
                close()
            } catch (ex: KryoNetException) {
                lastProtocolError = ex
                if (ERROR) {
                    if (isConnected)
                        error("kryonet", "Error updating connection: " + this, ex)
                    else
                        error("kryonet", "Error updating connection.", ex)
                }
                close()
                throw ex
            }

        }
        if (TRACE) trace("kryonet", "Client thread stopped.")
    }

    override fun start() {
        // Try to let any previous update thread stop.
        if (updateThread != null) {
            shutdown = true
            try {
                updateThread!!.join(5000)
            } catch (ignored: InterruptedException) {
            }

        }
        updateThread = Thread(this, "Client")
        updateThread!!.isDaemon = true
        updateThread!!.start()
    }

    override fun stop() {
        if (shutdown) return
        close()
        if (TRACE) trace("kryonet", "Client thread stopping.")
        shutdown = true
        selector.wakeup()
    }

    override fun close() {
        super.close()
        synchronized (updateLock) { // Blocks to avoid a select while the selector is used to bind the server connection.
        }
        // Select one last time to complete closing the socket.
        if (!isClosed) {
            isClosed = true
            selector.wakeup()
            try {
                selector.selectNow()
            } catch (ignored: IOException) {
            }

        }
    }

    /** Releases the resources used by this client, which may no longer be used.  */
    @Throws(IOException::class)
    fun dispose() {
        close()
        selector.close()
    }

    override fun addListener(listener: Listener) {
        super.addListener(listener)
        if (TRACE) trace("kryonet", "Client listener added.")
    }

    override fun removeListener(listener: Listener) {
        super.removeListener(listener)
        if (TRACE) trace("kryonet", "Client listener removed.")
    }

    /** An empty object will be sent if the UDP connection is inactive more than the specified milliseconds. Network hardware may
     * keep a translation table of inside to outside IP addresses and a UDP keep alive keeps this table entry from expiring. Set to
     * zero to disable. Defaults to 19000.  */
    fun setKeepAliveUDP(keepAliveMillis: Int) {
        if (udp == null) throw IllegalStateException("Not connected via UDP.")
        udp?.keepAliveMillis = keepAliveMillis
    }

    @Throws(IOException::class)
    private fun broadcast(udpPort: Int, socket: DatagramSocket) {
        val dataBuffer = ByteBuffer.allocate(64)
        serialization!!.write(null, dataBuffer, DiscoverHost())
        dataBuffer.flip()
        val data = ByteArray(dataBuffer.limit())
        dataBuffer.get(data)
        for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (address in Collections.list(iface.inetAddresses)) {
                // Java 1.5 doesn't support getting the subnet mask, so try the two most common.
                val ip = address.address
                ip[3] = -1 // 255.255.255.0
                try {
                    socket.send(DatagramPacket(data, data.size, InetAddress.getByAddress(ip), udpPort))
                } catch (ignored: Exception) {
                }

                ip[2] = -1 // 255.255.0.0
                try {
                    socket.send(DatagramPacket(data, data.size, InetAddress.getByAddress(ip), udpPort))
                } catch (ignored: Exception) {
                }

            }
        }
        if (DEBUG) debug("kryonet", "Broadcasted host discovery on port: " + udpPort)
    }

    /** Broadcasts a UDP message on the LAN to discover any running servers. The address of the first server to respond is returned.
     * @param udpPort The UDP port of the server.
     * *
     * @param timeoutMillis The number of milliseconds to wait for a response.
     * *
     * @return the first server found, or null if no server responded.
     */
    fun discoverHost(udpPort: Int, timeoutMillis: Int): InetAddress? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            broadcast(udpPort, socket)
            socket.soTimeout = timeoutMillis
            val packet = discoveryHandler.onRequestNewDatagramPacket()
            try {
                socket.receive(packet)
            } catch (ex: SocketTimeoutException) {
                if (INFO) info("kryonet", "Host discovery timed out.")
                return null
            }

            if (INFO) info("kryonet", "Discovered server: " + packet.address)
            discoveryHandler.onDiscoveredHost(packet, serialization)
            return packet.address
        } catch (ex: IOException) {
            if (ERROR) error("kryonet", "Host discovery failed.", ex)
            return null
        } finally {
            if (socket != null) socket.close()
            discoveryHandler.onFinally()
        }
    }

    /** Broadcasts a UDP message on the LAN to discover any running servers.
     * @param udpPort The UDP port of the server.
     * *
     * @param timeoutMillis The number of milliseconds to wait for a response.
     */
    fun discoverHosts(udpPort: Int, timeoutMillis: Int): List<InetAddress> {
        val hosts = ArrayList<InetAddress>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            broadcast(udpPort, socket)
            socket.soTimeout = timeoutMillis
            while (true) {
                val packet = discoveryHandler.onRequestNewDatagramPacket()
                try {
                    socket.receive(packet)
                } catch (ex: SocketTimeoutException) {
                    if (INFO) info("kryonet", "Host discovery timed out.")
                    return hosts
                }

                if (INFO) info("kryonet", "Discovered server: " + packet.address)
                discoveryHandler.onDiscoveredHost(packet, serialization)
                hosts.add(packet.address)
            }
        } catch (ex: IOException) {
            if (ERROR) error("kryonet", "Host discovery failed.", ex)
            return hosts
        } finally {
            if (socket != null) socket.close()
            discoveryHandler.onFinally()
        }
    }

    companion object {
        init {
            try {
                // Needed for NIO selectors on Android 2.2.
                System.setProperty("java.net.preferIPv6Addresses", "false")
            } catch (ignored: AccessControlException) {
            }

        }
    }
}
/** Creates a Client with a write buffer size of 8192 and an object buffer size of 2048.  */
/** @param writeBufferSize One buffer of this size is allocated. Objects are serialized to the write buffer where the bytes are
 * *           queued until they can be written to the TCP socket.
 * *
 *
 *
 * *           Normally the socket is writable and the bytes are written immediately. If the socket cannot be written to and
 * *           enough serialized objects are queued to overflow the buffer, then the connection will be closed.
 * *
 *
 *
 * *           The write buffer should be sized at least as large as the largest object that will be sent, plus some head room to
 * *           allow for some serialized objects to be queued in case the buffer is temporarily not writable. The amount of head
 * *           room needed is dependent upon the size of objects being sent and how often they are sent.
 * *
 * @param objectBufferSize One (using only TCP) or three (using both TCP and UDP) buffers of this size are allocated. These
 * *           buffers are used to hold the bytes for a single object graph until it can be sent over the network or
 * *           deserialized.
 * *
 *
 *
 * *           The object buffers should be sized at least as large as the largest object that will be sent or received.
 */
/** Opens a TCP only client.
 * @see .connect
 */
/** Calls [connect][.connect] with the values last passed to connect.
 * @throws IllegalStateException if connect has never been called.
 */
