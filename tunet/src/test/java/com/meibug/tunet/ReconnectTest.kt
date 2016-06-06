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
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicInteger

class ReconnectTest : KryoNetTestCase() {
    @Throws(IOException::class)
    fun testReconnect() {
        val timer = Timer()

        val server = Server()
        startEndPoint(server)
        server.bind(KryoNetTestCase.tcpPort)
        server.addListener(object : Listener() {
            override fun connected(connection: Connection) {
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        println("Disconnecting after 2 seconds.")
                        connection.close()
                    }
                }, 2000)
            }
        })

        // ----

        val reconnetCount = AtomicInteger()
        val client = Client()
        startEndPoint(client)
        client.addListener(object : Listener() {
            override fun disconnected(connection: Connection) {
                if (reconnetCount.andIncrement == 2) {
                    stopEndPoints()
                    return
                }
                object : Thread() {
                    override fun run() {
                        try {
                            println("Reconnecting: " + reconnetCount.get())
                            client.reconnect()
                        } catch (ex: IOException) {
                            ex.printStackTrace()
                        }

                    }
                }.start()
            }
        })
        client.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort)

        waitForThreads(10000)
        Assert.assertEquals(3, reconnetCount.andIncrement)
    }
}
