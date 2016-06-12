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

import org.junit.Assert
import java.io.IOException

class MultipleThreadTest : KryoNetTestCase() {
    internal var receivedServer: Int = 0
    internal var receivedClient1: Int = 0
    internal var receivedClient2: Int = 0

    @Throws(IOException::class)
    fun testMultipleThreads() {
        receivedServer = 0

        val messageCount = 10
        val threads = 5
        val sleepMillis = 50
        val clients = 3

        val server = Server(16384, 8192)
        startEndPoint(server)
        server.bind(KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)
        server.addListener(object : Listener() {
            override fun received(connection: Connection, obj: Any) {
                receivedServer++
                if (receivedServer == messageCount * clients) stopEndPoints()
            }
        })

        // ----

        for (i in 0..clients - 1) {
            val client = Client(16384, 8192)
            startEndPoint(client)
            client.addListener(object : Listener() {
                internal var received: Int = 0

                override fun received(connection: Connection, obj: Any) {
                    if (obj is String) {
                        received++
                        if (received == messageCount * threads) {
                            for (i in 0..messageCount - 1) {
                                connection.sendTCP("message" + i)
                                try {
                                    Thread.sleep(50)
                                } catch (ignored: InterruptedException) {
                                }

                            }
                        }
                    }
                }
            })
            client.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)
        }

        for (i in 0..threads - 1) {
            object : Thread() {
                override fun run() {
                    val connections = server.connections
                    for (i in 0..messageCount - 1) {
                        var ii = 0
                        val n = connections.size
                        while (ii < n) {
                            connections[ii].sendTCP("message" + i)
                            ii++
                        }
                        try {
                            Thread.sleep(sleepMillis.toLong())
                        } catch (ignored: InterruptedException) {
                        }

                    }
                }
            }.start()
        }

        waitForThreads(5000)

        Assert.assertEquals(messageCount * clients, receivedServer)
    }
}
