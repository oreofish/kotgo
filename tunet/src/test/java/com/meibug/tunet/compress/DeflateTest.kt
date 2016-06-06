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

package com.meibug.tunet.compress

import com.meibug.tunet.Client
import com.meibug.tunet.Connection
import com.meibug.tunet.KryoNetTestCase
import com.meibug.tunet.Listener
import com.meibug.tunet.Server

import java.io.IOException
import java.util.ArrayList

class DeflateTest : KryoNetTestCase() {
    @Throws(IOException::class)
    fun testDeflate() {
        val server = Server()

        val data = SomeData()
        data.text = "some text here aaaaaaaaaabbbbbbbbbbbcccccccccc"
        data.stuff = shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        val a = ArrayList<Int?>()
        a.add(12)
        a.add(null)
        a.add(34)

        startEndPoint(server)
        server.bind(KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)
        server.addListener(object : Listener() {
            override fun connected(connection: Connection) {
                server.sendToAllTCP(data)
                connection.sendTCP(data)
                connection.sendTCP(a)
            }
        })

        // ----

        val client = Client()
        startEndPoint(client)
        client.addListener(object : Listener() {
            override fun received(connection: Connection, obj: Any) {
                if (obj is SomeData) {
                    println(obj.stuff!![3])
                } else if (obj is ArrayList<*>) {
                    stopEndPoints()
                }
            }
        })
        client.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)

        waitForThreads()
    }

    class SomeData {
        var text: String? = null
        var stuff: ShortArray? = null
    }
}
