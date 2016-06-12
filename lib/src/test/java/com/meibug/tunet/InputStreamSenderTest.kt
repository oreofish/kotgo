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

import com.meibug.tunet.Client
import com.meibug.tunet.Connection
import com.meibug.tunet.Listener
import com.meibug.tunet.Server
import com.meibug.tunet.util.InputStreamSender
import org.junit.Assert

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class InputStreamSenderTest : KryoNetTestCase() {
    internal var success: Boolean = false

    @Throws(IOException::class)
    fun testStream() {
        val largeDataSize = 12345

        val server = Server(16384, 8192)
        startEndPoint(server)
        server.bind(KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)
        server.addListener(object : Listener() {
            override fun connected(connection: Connection) {
                val output = ByteArrayOutputStream(largeDataSize)
                for (i in 0..largeDataSize - 1)
                    output.write(i)
                val input = ByteArrayInputStream(output.toByteArray())
                // Send data in 512 byte chunks.
                connection.addListener(object : InputStreamSender(input, 512) {
                    override fun start() {
                        // Normally would send an object so the receiving side knows how to handle the chunks we are about to send.
                        println("starting")
                    }

                    override fun next(bytes: ByteArray): Any {
                        println("sending " + bytes.size)
                        return bytes // Normally would wrap the byte[] with an object so the receiving side knows how to handle it.
                    }
                })
            }
        })

        // ----

        val client = Client(16384, 8192)
        startEndPoint(client)
        client.addListener(object : Listener() {
            internal var total: Int = 0

            override fun received(connection: Connection, obj: Any) {
                if (obj is ByteArray) {
                    val length = obj.size
                    println("received " + length)
                    total += length
                    if (total == largeDataSize) {
                        success = true
                        stopEndPoints()
                    }
                }
            }
        })

        client.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)

        waitForThreads(5000)
        if (!success) Assert.fail()
    }
}
