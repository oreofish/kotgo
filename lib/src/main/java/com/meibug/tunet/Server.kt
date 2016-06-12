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
import java.net.InetSocketAddress
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap

import com.meibug.tunet.util.Log.DEBUG
import com.meibug.tunet.util.Log.ERROR
import com.meibug.tunet.util.Log.INFO
import com.meibug.tunet.util.Log.TRACE
import com.meibug.tunet.util.Log.WARN
import com.meibug.tunet.util.Log.debug
import com.meibug.tunet.util.Log.error
import com.meibug.tunet.util.Log.info
import com.meibug.tunet.util.Log.trace
import com.meibug.tunet.util.Log.warn
import java.util.concurrent.CopyOnWriteArrayList

/** Manages TCP and optionally UDP connections from many [Clients][Client].
 * @author Nathan Sweet @n4te.com>
 */
class Server @JvmOverloads constructor(private val writeBufferSize: Int = 16384, private val objectBufferSize: Int = 2048, override val serialization: Serialization = JsonSerialization()) : EndPoint {
    private val selector: Selector
    private var emptySelects: Int = 0
    private var serverChannel: ServerSocketChannel? = null
    private var udp: UdpConnection? = null
    /** Returns the current connections. The array returned should not be modified.  */
    var connections = CopyOnWriteArrayList<Connection>()
        private set
    private val pendingConnections = HashMap<Int, Connection>()
    internal var listeners = arrayListOf<Listener>()
    private val listenerLock = Object()
    private var nextConnectionID = 1
    @Volatile private var shutdown: Boolean = false
    private val updateLock = Object()
    override lateinit var updateThread: Thread
    private var discoveryHandler: ServerDiscoveryHandler? = null

    private val dispatchListener = object : Listener() {
        override fun connected(connection: Connection) {
            val listeners = this@Server.listeners
            for (listener in listeners) listener.connected(connection)
        }

        override fun disconnected(connection: Connection) {
            removeConnection(connection)
            val listeners = this@Server.listeners
            for (listener in listeners) listener.disconnected(connection)
        }

        override fun received(connection: Connection, obj: Any) {
            val listeners = this@Server.listeners
            for (listener in listeners) listener.received(connection, obj)
        }

        override fun idle(connection: Connection) {
            val listeners = this@Server.listeners
            for (listener in listeners) listener.idle(connection)
        }
    }

    init {

        this.discoveryHandler = ServerDiscoveryHandler.DEFAULT

        try {
            selector = Selector.open()
        } catch (ex: IOException) {
            throw RuntimeException("Error opening selector.", ex)
        }

    }

    fun setDiscoveryHandler(newDiscoveryHandler: ServerDiscoveryHandler) {
        discoveryHandler = newDiscoveryHandler
    }

    /** Opens a TCP only server.
     * @throws IOException if the server could not be opened.
     */
    @Throws(IOException::class)
    fun bind(tcpPort: Int) {
        bind(InetSocketAddress(tcpPort), null)
    }

    /** Opens a TCP and UDP server.
     * @throws IOException if the server could not be opened.
     */
    @Throws(IOException::class)
    fun bind(tcpPort: Int, udpPort: Int) {
        bind(InetSocketAddress(tcpPort), InetSocketAddress(udpPort))
    }

    /** @param udpPort May be null.
     */
    @Throws(IOException::class)
    fun bind(tcpPort: InetSocketAddress, udpPort: InetSocketAddress?) {
        close()
        synchronized (updateLock) {
            selector.wakeup()
            try {
                serverChannel = selector.provider().openServerSocketChannel()
                serverChannel!!.socket().bind(tcpPort)
                serverChannel!!.configureBlocking(false)
                serverChannel!!.register(selector, SelectionKey.OP_ACCEPT)
                if (DEBUG) debug("kryonet", "Accepting connections on port: $tcpPort/TCP")

                if (udpPort != null) {
                    udp = UdpConnection(serialization, objectBufferSize)
                    udp!!.bind(selector, udpPort)
                    if (DEBUG) debug("kryonet", "Accepting connections on port: $udpPort/UDP")
                }
            } catch (ex: IOException) {
                close()
                throw ex
            }

        }
        if (INFO) info("kryonet", "Server opened.")
    }

    /** Accepts any new connections and reads or writes any pending data for the current connections.
     * @param timeout Wait for up to the specified milliseconds for a connection to be ready to process. May be zero to return
     * *           immediately if there are no connections to process.
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
            val keys = selector.selectedKeys()
            synchronized (keys) {
                val udp = this.udp
                val iter = keys.iterator()
                outer@ while (iter.hasNext()) {
                    keepAlive()
                    val selectionKey = iter.next()
                    iter.remove()
                    var fromConnection = selectionKey.attachment() as Connection?
                    try {
                        val ops = selectionKey.readyOps()

                        if (fromConnection != null) { // Must be a TCP read or write operation.
                            if (udp != null && fromConnection.udpRemoteAddress == null) {
                                fromConnection.close()
                                continue
                            }
                            if (ops and SelectionKey.OP_READ == SelectionKey.OP_READ) {
                                try {
                                    while (true) {
                                        val obj = fromConnection.tcp.readObject(fromConnection) ?: break
                                        if (DEBUG) {
                                            val objectString = if (obj == null) "null" else obj.javaClass.simpleName
                                            if (obj !is FrameworkMessage) {
                                                debug("kryonet", fromConnection.toString() + " received TCP: " + objectString)
                                            } else if (TRACE) {
                                                trace("kryonet", fromConnection.toString() + " received TCP: " + objectString)
                                            }
                                        }
                                        fromConnection.notifyReceived(obj)
                                    }
                                } catch (ex: IOException) {
                                    if (TRACE) {
                                        trace("kryonet", "Unable to read TCP from: " + fromConnection, ex)
                                    } else if (DEBUG) {
                                        debug("kryonet", fromConnection.toString() + " update: " + ex.message)
                                    }
                                    fromConnection.close()
                                } catch (ex: KryoNetException) {
                                    if (ERROR) error("kryonet", "Error reading TCP from connection: " + fromConnection, ex)
                                    fromConnection.close()
                                }

                            }
                            if (ops and SelectionKey.OP_WRITE == SelectionKey.OP_WRITE) {
                                try {
                                    fromConnection.tcp.writeOperation()
                                } catch (ex: IOException) {
                                    if (TRACE) {
                                        trace("kryonet", "Unable to write TCP to connection: " + fromConnection, ex)
                                    } else if (DEBUG) {
                                        debug("kryonet", fromConnection.toString() + " update: " + ex.message)
                                    }
                                    fromConnection.close()
                                }

                            }
                            continue
                        }

                        if (ops and SelectionKey.OP_ACCEPT == SelectionKey.OP_ACCEPT) {
                            val serverChannel = this.serverChannel ?: continue
                            try {
                                val socketChannel = serverChannel.accept()
                                if (socketChannel != null) acceptOperation(socketChannel)
                            } catch (ex: IOException) {
                                if (DEBUG) debug("kryonet", "Unable to accept new connection.", ex)
                            }

                            continue
                        }

                        // Must be a UDP read operation.
                        if (udp == null) {
                            selectionKey.channel().close()
                            continue
                        }
                        val fromAddress: InetSocketAddress?
                        try {
                            fromAddress = udp.readFromAddress()
                        } catch (ex: IOException) {
                            if (WARN) warn("kryonet", "Error reading UDP data.", ex)
                            continue
                        }

                        if (fromAddress == null) continue

                        val connections = this.connections
                        var i = 0
                        val n = connections.size
                        while (i < n) {
                            val connection = connections[i]
                            if (fromAddress == connection.udpRemoteAddress) {
                                fromConnection = connection
                                break
                            }
                            i++
                        }

                        val obj: Any?
                        try {
                            obj = udp.readObject(fromConnection)
                        } catch (ex: KryoNetException) {
                            if (WARN) {
                                if (fromConnection != null) {
                                    if (ERROR) error("kryonet", "Error reading UDP from connection: " + fromConnection, ex)
                                } else
                                    warn("kryonet", "Error reading UDP from unregistered address: " + fromAddress, ex)
                            }
                            continue
                        }

                        if (obj is FrameworkMessage) {
                            if (obj is RegisterUDP) {
                                // Store the fromAddress on the connection and reply over TCP with a RegisterUDP to indicate success.
                                val fromConnectionID = obj.connectionID
                                val connection = pendingConnections.remove(fromConnectionID)
                                if (connection != null) {
                                    if (connection.udpRemoteAddress != null) continue@outer
                                    connection.udpRemoteAddress = fromAddress
                                    addConnection(connection)
                                    connection.sendTCP(RegisterUDP())
                                    if (DEBUG)
                                        debug("kryonet", "Port " + udp.datagramChannel!!.socket().localPort + "/UDP connected to: "
                                                + fromAddress)
                                    connection.notifyConnected()
                                    continue
                                }
                                if (DEBUG)
                                    debug("kryonet", "Ignoring incoming RegisterUDP with invalid connection ID: " + fromConnectionID)
                                continue
                            }
                            if (obj is DiscoverHost) {
                                try {
                                    discoveryHandler!!.onDiscoverHost(udp.datagramChannel!!, fromAddress, serialization)
                                    if (DEBUG) debug("kryonet", "Responded to host discovery from: " + fromAddress)
                                } catch (ex: IOException) {
                                    if (WARN) warn("kryonet", "Error replying to host discovery from: " + fromAddress, ex)
                                }

                                continue
                            }
                        }

                        if (fromConnection != null) {
                            if (DEBUG) {
                                val objectString = if (obj == null) "null" else obj.javaClass.simpleName
                                if (obj is FrameworkMessage) {
                                    if (TRACE) trace("kryonet", fromConnection.toString() + " received UDP: " + objectString)
                                } else
                                    debug("kryonet", fromConnection.toString() + " received UDP: " + objectString)
                            }
                            fromConnection.notifyReceived(obj)
                            continue
                        }
                        if (DEBUG) debug("kryonet", "Ignoring UDP from unregistered address: " + fromAddress)
                    } catch (ex: CancelledKeyException) {
                        if (fromConnection != null)
                            fromConnection.close()
                        else
                            selectionKey.channel().close()
                    }

                }
            }
        }
        val time = System.currentTimeMillis()
        connections.map {
            if (it.tcp.isTimedOut(time)) {
                if (DEBUG) debug("kryonet", it.toString() + " timed out.")
                it.close()
            } else {
                if (it.tcp.needsKeepAlive(time)) it.sendTCP(FrameworkMessage.keepAlive)
            }
            if (it.isIdle) it.notifyIdle()
        }
    }

    private fun keepAlive() {
        val time = System.currentTimeMillis()
        for (connection in connections) {
            if (connection.tcp.needsKeepAlive(time)) connection.sendTCP(FrameworkMessage.keepAlive)
        }
    }

    override fun run() {
        if (TRACE) trace("kryonet", "Server thread started.")
        shutdown = false
        while (!shutdown) {
            try {
                update(250)
            } catch (ex: IOException) {
                if (ERROR) error("kryonet", "Error updating server connections.", ex)
                close()
            }

        }
        if (TRACE) trace("kryonet", "Server thread stopped.")
    }

    override fun start() {
        Thread(this, "Server").start()
    }

    override fun stop() {
        if (shutdown) return
        close()
        if (TRACE) trace("kryonet", "Server thread stopping.")
        shutdown = true
    }

    private fun acceptOperation(socketChannel: SocketChannel) {
        val connection = Connection(serialization, writeBufferSize, objectBufferSize)
        connection.endPoint = this
        val udp = this.udp
        if (udp != null) connection.udp = udp
        try {
            val selectionKey = connection.tcp.accept(selector, socketChannel)
            selectionKey!!.attach(connection)

            val id = nextConnectionID++
            if (nextConnectionID == -1) nextConnectionID = 1
            connection.id = id
            connection.isConnected = true
            connection.addListener(dispatchListener)

            if (udp == null)
                addConnection(connection)
            else
                pendingConnections.put(id, connection)

            val registerConnection = RegisterTCP()
            registerConnection.connectionID = id
            connection.sendTCP(registerConnection)

            if (udp == null) connection.notifyConnected()
        } catch (ex: IOException) {
            connection.close()
            if (DEBUG) debug("kryonet", "Unable to accept TCP connection.", ex)
        }

    }

    /** Allows the connections used by the server to be subclassed. This can be useful for storage per connection without an
     * additional lookup.  */
    /*
	protected Connection newConnection () {
		return new Connection();
	}
*/

    private fun addConnection(connection: Connection) {
        connections.add(0, connection)
    }

    internal fun removeConnection(connection: Connection) {
        connections.remove(connection)
        pendingConnections.remove(connection.id)
    }

    // BOZO - Provide mechanism for sending to multiple clients without serializing multiple times.

    fun sendToAllTCP(obj: Any) = connections.map { it.sendTCP(obj) }

    fun sendToAllExceptTCP(connectionID: Int, obj: Any) = connections.map { if (it.id != connectionID) it.sendTCP(obj) }

    fun sendToTCP(connectionID: Int, obj: Any) {
        for (connection in connections) {
            if (connection.id == connectionID) {
                connection.sendTCP(obj)
                break
            }
        }
    }

    fun sendToAllUDP(obj: Any) = connections.map { it.sendUDP(obj) }

    fun sendToAllExceptUDP(connectionID: Int, obj: Any) = connections.map { if (it.id != connectionID) it.sendUDP(obj) }

    fun sendToUDP(connectionID: Int, obj: Any) {
        for (connection in connections) {
            if (connection.id == connectionID) {
                connection.sendUDP(obj)
                break
            }
        }
    }

    override fun addListener(listener: Listener) {
        // if (listener == null) throw IllegalArgumentException("listener cannot be null.")
        synchronized (listenerLock) {
            if (listeners.contains(listener)) return
            listeners.add(0, listener)
        }
        if (TRACE) trace("kryonet", "Server listener added: " + listener.javaClass.name)
    }

    override fun removeListener(listener: Listener) {
        // if (listener == null) throw IllegalArgumentException("listener cannot be null.")
        synchronized (listenerLock) {
            listeners.remove(listener)
        }
        if (TRACE) trace("kryonet", "Server listener removed: " + listener.javaClass.name)
    }

    /** Closes all open connections and the server port(s).  */
    override fun close() {
        if (INFO && connections.size > 0) info("kryonet", "Closing server connections...")
        for (connection in connections) {
            connection.close()
        }
        connections = CopyOnWriteArrayList<Connection>()

        try {
            serverChannel?.close()
            if (INFO) info("kryonet", "Server closed.")
        } catch (ex: IOException) {
            if (DEBUG) debug("kryonet", "Unable to close server.", ex)
        }
        serverChannel = null

        udp?.close()
        udp = null

        synchronized (updateLock) { // Blocks to avoid a select while the selector is used to bind the server connection.
        }
        // Select one last time to complete closing the socket.
        selector.wakeup()
        try {
            selector.selectNow()
        } catch (ignored: IOException) {
        }

    }

    /** Releases the resources used by this server, which may no longer be used.  */
    @Throws(IOException::class)
    fun dispose() {
        close()
        selector.close()
    }
}
/** Creates a Server with a write buffer size of 16384 and an object buffer size of 2048.  */
/** @param writeBufferSize One buffer of this size is allocated for each connected client. Objects are serialized to the write
 * *           buffer where the bytes are queued until they can be written to the TCP socket.
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
