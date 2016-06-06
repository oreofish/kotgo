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
import java.util.Arrays

class JsonTest : KryoNetTestCase() {
    private var fail: String? = null

    @Throws(IOException::class)
    fun testJson() {
        var fail: String? = null

        val dataTCP = Data()
        populateData(dataTCP, true)
        val dataUDP = Data()
        populateData(dataUDP, false)

        val server = Server(16384, 8192)
        startEndPoint(server)
        server.bind(KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)
        server.addListener(object : Listener() {
            override fun connected(connection: Connection) {
                connection.sendTCP(dataTCP)
                connection.sendUDP(dataUDP) // Note UDP ping pong stops if a UDP packet is lost.
            }

            override fun received(connection: Connection, obj: Any) {
                if (obj is Data) {
                    if (obj.isTCP) {
                        if (obj != dataTCP) {
                            fail = "TCP data is not equal on server."
                            throw RuntimeException("Fail!")
                        }
                        connection.sendTCP(obj)
                    } else {
                        if (obj != dataUDP) {
                            fail = "UDP data is not equal on server."
                            throw RuntimeException("Fail!")
                        }
                        connection.sendUDP(obj)
                    }
                }
            }
        })

        // ----
        val client = Client(16384, 8192)
        startEndPoint(client)
        client.addListener(object : Listener() {
            override fun received(connection: Connection, obj: Any) {
                if (obj is Data) {
                    if (obj.isTCP) {
                        if (obj != dataTCP) {
                            fail = "TCP data is not equal on client."
                            throw RuntimeException("Fail!")
                        }
                        connection.sendTCP(obj)
                    } else {
                        if (obj != dataUDP) {
                            fail = "UDP data is not equal on client."
                            throw RuntimeException("Fail!")
                        }
                        connection.sendUDP(obj)
                    }
                }
            }
        })

        client.connect(5000, KryoNetTestCase.host, KryoNetTestCase.tcpPort, KryoNetTestCase.udpPort)

        waitForThreads(5000)

        if (fail != null) Assert.fail(fail)
    }

    private fun populateData(data: Data, isTCP: Boolean) {
        data.isTCP = isTCP

        val buffer = StringBuffer()
        for (i in 0..2999)
            buffer.append('a')
        data.string = buffer.toString()

        data.strings = arrayOf<String>("abcdefghijklmnopqrstuvwxyz0123456789", "", "null", "!@#$", "�����")
        data.ints = intArrayOf(-1234567, 1234567, -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE)
        data.shorts = shortArrayOf(-12345, 12345, -1, 0, 1, java.lang.Short.MAX_VALUE, java.lang.Short.MIN_VALUE)
        data.floats = floatArrayOf(0f, 1f, -1f, 123456f, -123456f, 0.1f, 0.2f, -0.3f, Math.PI.toFloat(), java.lang.Float.MAX_VALUE, java.lang.Float.MIN_VALUE)
        data.bytes = byteArrayOf(-123, 123, -1, 0, 1, java.lang.Byte.MAX_VALUE, java.lang.Byte.MIN_VALUE)
        data.booleans = booleanArrayOf(true, false)
        data.Ints = arrayOf(-1234567, 1234567, -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE)
        data.Shorts = arrayOf(-12345, 12345, -1, 0, 1, java.lang.Short.MAX_VALUE, java.lang.Short.MIN_VALUE)
        data.Floats = arrayOf(0f, 1f, -1f, 123456f, -123456f, 0.1f, 0.2f, -0.3f, Math.PI.toFloat(), java.lang.Float.MAX_VALUE, java.lang.Float.MIN_VALUE)
        data.Bytes = arrayOf(-123, 123, -1, 0, 1, java.lang.Byte.MAX_VALUE, java.lang.Byte.MIN_VALUE)
        data.Booleans = arrayOf(true, false)
    }

    class Data {
        var string: String? = null
        var strings: Array<String>? = null
        var ints: IntArray? = null
        var shorts: ShortArray? = null
        var floats: FloatArray? = null
        var bytes: ByteArray? = null
        var booleans: BooleanArray? = null
        var Ints: Array<Int>? = null
        var Shorts: Array<Short>? = null
        var Floats: Array<Float>? = null
        var Bytes: Array<Byte>? = null
        var Booleans: Array<Boolean>? = null
        var isTCP: Boolean = false

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + Arrays.hashCode(Booleans)
            result = prime * result + Arrays.hashCode(Bytes)
            result = prime * result + Arrays.hashCode(Floats)
            result = prime * result + Arrays.hashCode(Ints)
            result = prime * result + Arrays.hashCode(Shorts)
            result = prime * result + Arrays.hashCode(booleans)
            result = prime * result + Arrays.hashCode(bytes)
            result = prime * result + Arrays.hashCode(floats)
            result = prime * result + Arrays.hashCode(ints)
            result = prime * result + if (isTCP) 1231 else 1237
            result = prime * result + Arrays.hashCode(shorts)
            result = prime * result + if (string == null) 0 else string!!.hashCode()
            result = prime * result + Arrays.hashCode(strings)
            return result
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) return true
            if (obj == null) return false
            if (javaClass != obj.javaClass) return false
            val other = obj as Data?
            if (!Arrays.equals(Booleans, other!!.Booleans)) return false
            if (!Arrays.equals(Bytes, other.Bytes)) return false
            if (!Arrays.equals(Floats, other.Floats)) return false
            if (!Arrays.equals(Ints, other.Ints)) return false
            if (!Arrays.equals(Shorts, other.Shorts)) return false
            if (!Arrays.equals(booleans, other.booleans)) return false
            if (!Arrays.equals(bytes, other.bytes)) return false
            if (!Arrays.equals(floats, other.floats)) return false
            if (!Arrays.equals(ints, other.ints)) return false
            if (isTCP != other.isTCP) return false
            if (!Arrays.equals(shorts, other.shorts)) return false
            if (string == null) {
                if (other.string != null) return false
            } else if (string != other.string) return false
            if (!Arrays.equals(strings, other.strings)) return false
            return true
        }

        override fun toString(): String {
            return "Data"
        }
    }
}
