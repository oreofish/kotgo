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
import java.util.concurrent.atomic.AtomicInteger

class MultipleServerTest : KryoNetTestCase() {
    internal var received = AtomicInteger()

    @Throws(IOException::class)
    fun testMultipleThreads() {
        val server1 = Server(16384, 8192)
        startEndPoint(server1)
        server1.bind(KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)
        server1.addListener(object : Listener() {
            override fun received(connection: Connection, obj: Any) {
                if (obj is String) {
                    if (obj != "client1") Assert.fail()
                    if (received.incrementAndGet() == 2) stopEndPoints()
                }
            }
        })

        val server2 = Server(16384, 8192)
        startEndPoint(server2)
        server2.bind(KryoNetTestCase.tcpPort + 1, KryoNetTestCase.udpPort + 1)
        server2.addListener(object : Listener() {
            override fun received(connection: Connection, obj: Any) {
                if (obj is String) {
                    if (obj != "client2") Assert.fail()
                    if (received.incrementAndGet() == 2) stopEndPoints()
                }
            }
        })

        // ----

        val client1 = Client(16384, 8192)
        startEndPoint(client1)
        client1.addListener(object : Listener() {
            override fun connected(connection: Connection) {
                connection.sendTCP("client1")
            }
        })
        client1.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)

        val client2 = Client(16384, 8192)
        startEndPoint(client2)
        client2.addListener(object : Listener() {
            override fun connected(connection: Connection) {
                connection.sendTCP("client2")
            }
        })
        client2.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort + 1, KryoNetTestCase.udpPort + 1)

        waitForThreads(5000)
    }
}