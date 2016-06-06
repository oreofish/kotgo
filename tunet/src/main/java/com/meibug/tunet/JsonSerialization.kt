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

import com.esotericsoftware.jsonbeans.Json
import com.esotericsoftware.jsonbeans.JsonException
import com.google.gson.Gson
import com.meibug.tunet.FrameworkMessage.DiscoverHost
import com.meibug.tunet.FrameworkMessage.KeepAlive
import com.meibug.tunet.FrameworkMessage.Ping
import com.meibug.tunet.FrameworkMessage.RegisterTCP
import com.meibug.tunet.FrameworkMessage.RegisterUDP

import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer

import com.meibug.tunet.util.Log.*
import com.meibug.tunet.util.Log.INFO
import com.meibug.tunet.util.Log.info
import java.nio.charset.Charset

class JsonSerialization (): Serialization {
    private val gson = Gson()
    private var logging = true
    private var prettyPrint = true
    private val logBuffer = byteArrayOf()

    fun setLogging(logging: Boolean, prettyPrint: Boolean) {
        this.logging = logging
        this.prettyPrint = prettyPrint
    }

    override fun write(connection: Connection, buffer: ByteBuffer, obj: Any) {
        val objClass = obj.javaClass.name
        val str = objClass + ":" + gson.toJson(obj);
        val bytes = str.toByteArray(charset("UTF-8"));
        try {
            buffer.put(bytes)
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }

        if (INFO && logging) {
            val message = str
            info("Wrote: " + message)
        }
    }

    override fun read(connection: Connection?, buffer: ByteBuffer): Any {
        val position = buffer.position()
        val limit = buffer.limit()
        val bytes = ByteArray(limit - position)
        buffer.get(bytes)
        val str = String( bytes, Charset.forName("UTF-8") )
        val index = str.indexOf(":")
        val strClass = str.substring(0, index)
        val objClass = Class.forName(strClass);
        val jsonStr = str.substring(index + 1)
        return gson.fromJson(jsonStr, objClass)
    }

    override fun writeLength(buffer: ByteBuffer, length: Int) {
        buffer.putInt(length)
    }

    override fun readLength(buffer: ByteBuffer): Int {
        return buffer.int
    }

    override val lengthLength: Int
        get() = 4
}
