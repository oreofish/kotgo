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

package com.meibug.tunet;

import com.esotericsoftware.jsonbeans.Json;
import com.esotericsoftware.jsonbeans.JsonException;
import com.google.gson.Gson;
import com.meibug.tunet.FrameworkMessage.DiscoverHost;
import com.meibug.tunet.FrameworkMessage.KeepAlive;
import com.meibug.tunet.FrameworkMessage.Ping;
import com.meibug.tunet.FrameworkMessage.RegisterTCP;
import com.meibug.tunet.FrameworkMessage.RegisterUDP;

import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import static com.meibug.tunet.util.Log.*;
import static com.meibug.tunet.util.Log.INFO;
import static com.meibug.tunet.util.Log.info;

public class JsonSerialization implements Serialization {
	private final Gson json = new Gson();
	private boolean logging = true, prettyPrint = true;
	private byte[] logBuffer = {};

	public JsonSerialization () {
	}

	public void setLogging (boolean logging, boolean prettyPrint) {
		this.logging = logging;
		this.prettyPrint = prettyPrint;
	}

	public void write (Connection connection, ByteBuffer buffer, Object object) {
		String str = (String)object;
		try {
			buffer.put(str.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		if (INFO && logging) {
			String message = new String(str);
			info("Wrote: " + message);
		}
	}

	public Object read (Connection connection, ByteBuffer buffer) {
		return buffer.toString();
	}

	public void writeLength (ByteBuffer buffer, int length) {
		buffer.putInt(length);
	}

	public int readLength (ByteBuffer buffer) {
		return buffer.getInt();
	}

	public int getLengthLength () {
		return 4;
	}
}
