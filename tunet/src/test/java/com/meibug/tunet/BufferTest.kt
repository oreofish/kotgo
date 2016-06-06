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
import java.util.concurrent.atomic.AtomicInteger

class BufferTest : KryoNetTestCase() {
    internal var received = AtomicInteger()
    internal var receivedBytes = AtomicInteger()

    @Throws(IOException::class)
    fun testManyLargeMessages() {
        val messageCount = 1024
        val objectBufferSize = 10250
        val writeBufferSize = 10250 * messageCount

        val server = Server(writeBufferSize, objectBufferSize)
        startEndPoint(server)
        server.bind(KryoNetTestCase.tcpPort)

        server.addListener(object : Listener() {
            internal var received = AtomicInteger()
            internal var receivedBytes = AtomicInteger()

            override fun received(connection: Connection, obj: Any) {
                if (obj is LargeMessage) {
                    println("Server sending message: " + received.get())
                    connection.sendTCP(obj)

                    receivedBytes.addAndGet(obj.bytes.size)

                    val count = received.incrementAndGet()
                    println("Server received $count messages.")
                    if (count == messageCount) {
                        println("Server received all $messageCount messages!")
                        println("Server received and sent " + receivedBytes.get() + " bytes.")
                    }
                }
            }
        })

        val client = Client(writeBufferSize, objectBufferSize)
        startEndPoint(client)
        client.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort)

        client.addListener(object : Listener() {
            internal var received = AtomicInteger()
            internal var receivedBytes = AtomicInteger()

            override fun received(connection: Connection, obj: Any) {
                if (obj is LargeMessage) {
                    val count = received.incrementAndGet()
                    println("Client received $count messages.")
                    if (count == messageCount) {
                        println("Client received all $messageCount messages!")
                        println("Client received and sent " + receivedBytes.get() + " bytes.")
                        stopEndPoints()
                    }
                }
            }
        })

        val b = ByteArray(1024 * 10)
        for (i in 0..messageCount - 1) {
            println("Client sending: " + i)
            client.sendTCP(LargeMessage(b))
        }
        println("Client has queued $messageCount messages.")

        waitForThreads(5000)
    }

    class LargeMessage (val bytes: ByteArray) {
    }
}
