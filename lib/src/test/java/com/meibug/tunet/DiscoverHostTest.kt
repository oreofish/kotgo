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


import com.meibug.tunet.util.Log
import org.junit.Assert

import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class DiscoverHostTest : KryoNetTestCase() {

    @Throws(IOException::class)
    fun testBroadcast() {
        // This server exists solely to reply to Client#discoverHost.
        // It wouldn't be needed if the real server was using UDP.
        val broadcastServer = Server()
        startEndPoint(broadcastServer)
        broadcastServer.bind(0, KryoNetTestCase.udpPort)

        val server = Server()
        startEndPoint(server)
        server.bind(54555)
        server.addListener(object : Listener() {
            override fun disconnected(connection: Connection) {
                broadcastServer.stop()
                server.stop()
            }
        })

        // ----

        val client = Client()
        val host = client.discoverHost(KryoNetTestCase.udpPort, 2000)
        if (host == null) {
            stopEndPoints()
            Assert.fail("No servers found.")
            return
        }

        startEndPoint(client)
        client.connect(2000, host, KryoNetTestCase.tcpPort)
        client.stop()

        waitForThreads()
    }

    @Throws(IOException::class)
    fun testCustomBroadcast() {

        val serverDiscoveryHandler = object: ServerDiscoveryHandler {
            override fun onDiscoverHost(datagramChannel: DatagramChannel, fromAddress: InetSocketAddress, serialization: Serialization): Boolean {
                val packet = DiscoveryResponsePacket()
                packet.id = 42
                packet.gameName = "gameName"
                packet.playerName = "playerName"

                val buffer = ByteBuffer.allocate(256)
                serialization.write(null, buffer, packet)
                buffer.flip()

                datagramChannel.send(buffer, fromAddress)

                return true
            }
        }

        val clientDiscoveryHandler = object : ClientDiscoveryHandler {
            private var input: Input? = null

            override fun onRequestNewDatagramPacket(): DatagramPacket {
                val buffer = ByteArray(1024)
                input = Input(buffer)
                return DatagramPacket(buffer, buffer.size)
            }

            override fun onDiscoveredHost(datagramPacket: DatagramPacket, serialization: Serialization) {
                if (input?.buffer != null) {
                    val packet: DiscoveryResponsePacket
                    // packet = (DiscoveryResponsePacket)kryo.readClassAndObject(input);
                    val buf = ByteBuffer.wrap(input?.buffer)
                    buf.limit(datagramPacket.length)
                    packet = serialization.read(null, buf) as DiscoveryResponsePacket
                    Log.info("test", "packet.id = " + packet.id)
                    Log.info("test", "packet.gameName = " + packet.gameName!!)
                    Log.info("test", "packet.playerName = " + packet.playerName!!)
                    Log.info("test", "datagramPacket.getAddress() = " + datagramPacket.address)
                    Log.info("test", "datagramPacket.getPort() = " + datagramPacket.port)
                    Assert.assertEquals(42, packet.id)
                    Assert.assertEquals("gameName", packet.gameName)
                    Assert.assertEquals("playerName", packet.playerName)
                    Assert.assertEquals(KryoNetTestCase.udpPort, datagramPacket.port)
                }
            }

            override fun onFinally() {
                input?.close()
            }
        }

        // This server exists solely to reply to Client#discoverHost.
        // It wouldn't be needed if the real server was using UDP.
        val broadcastServer = Server()
        broadcastServer.setDiscoveryHandler(serverDiscoveryHandler)

        startEndPoint(broadcastServer)
        broadcastServer.bind(0, KryoNetTestCase.udpPort)

        val server = Server()
        startEndPoint(server)
        server.bind(54555)
        server.addListener(object : Listener() {
            override fun disconnected(connection: Connection) {
                broadcastServer.stop()
                server.stop()
            }
        })

        // ----

        val client = Client()
        client.discoveryHandler = clientDiscoveryHandler

        val host = client.discoverHost(KryoNetTestCase.udpPort, 2000)
        if (host == null) {
            stopEndPoints()
            Assert.fail("No servers found.")
            return
        }

        startEndPoint(client)
        client.connect(2000, host, KryoNetTestCase.tcpPort)
        client.stop()

        waitForThreads()
    }

    class DiscoveryResponsePacket {

        var id: Int = 0
        var gameName: String? = null
        var playerName: String? = null
    }//

    class Input(val buffer: ByteArray) : InputStream() {
        protected var position: Int = 0
        protected var capacity: Int = buffer.size
        protected var limit: Int = buffer.size
        protected var total: Long = 0
        protected var chars = CharArray(32)
        protected var inputStream: InputStream? = null

        @Throws(IOException::class)
        override fun read(): Int {
            return 0
        }

        override fun close() {
            if (inputStream != null) {
                try {
                    inputStream!!.close()
                } catch (ignored: IOException) {
                }

            }
        }
    }
}
