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
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean

object KryoNetBufferUnderflowTest {
    @Throws(IOException::class, InterruptedException::class)
    @JvmStatic fun main(args: Array<String>) {
        val port = 7000
        val writeBufferSize = 16384
        val objectBufferSize = 2048
        val received = AtomicBoolean()

        // Creating server
        val server = Server(writeBufferSize, objectBufferSize)
        server.bind(port)
        server.start()
        println("Server listening on port " + port)

        // Creating client
        val client = Client(writeBufferSize, objectBufferSize)
        client.start()
        client.addListener(object : Listener() {
            override fun received(connection: Connection, obj: Any) {
                if (obj is String) {
                    println("Received: " + obj)
                    received.set(true)
                } else
                    System.err.println("Received unexpected object")
            }
        })
        client.connect(5000, "localhost", port)
        println("Client connected")

        // Catching exception
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            e.printStackTrace()
            received.set(true)
            // Stopping it all
            println("Stopping client and server")
            client.stop()
            server.stop()
        }

        // Sending small messages
        for (i in 0..4) {
            val smallMessage = "RandomStringUtils.randomAlphanumeric(256)"
            println("Sending: " + smallMessage)
            received.set(false)
            server.sendToAllTCP(smallMessage)
            while (!received.get()) {
                Thread.sleep(100)
            }
        }

        // Sending large message
        var bigMessage = "RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)"
        bigMessage = bigMessage + bigMessage + bigMessage + bigMessage + bigMessage + bigMessage + bigMessage
        println("Sending: " + bigMessage)
        received.set(false)
        server.sendToAllTCP(bigMessage)
        while (!received.get()) {
            Thread.sleep(100)
        }
    }
}
