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

import com.meibug.tunet.FrameworkMessage.DiscoverHost

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

interface ServerDiscoveryHandler {

    /** Called when the [Server] receives a [DiscoverHost] packet.
     * @param fromAddress [InetSocketAddress] the [DiscoverHost] came from
     * *
     * @param serialization the [Server]'s [Serialization] instance
     * *
     * @return true if a response was sent to `fromAddress`, false otherwise
     * *
     * @throws IOException from the use of [DatagramChannel.send]
     */
    @Throws(IOException::class)
    fun onDiscoverHost(datagramChannel: DatagramChannel, fromAddress: InetSocketAddress, serialization: Serialization): Boolean

    companion object {
        /** This implementation of [ServerDiscoveryHandler] is responsible for providing the [Server] with it's default
         * behavior.  */
        val DEFAULT: ServerDiscoveryHandler = object : ServerDiscoveryHandler {
            private val emptyBuffer = ByteBuffer.allocate(0)

            @Throws(IOException::class)
            override fun onDiscoverHost(datagramChannel: DatagramChannel, fromAddress: InetSocketAddress, serialization: Serialization): Boolean {
                datagramChannel.send(emptyBuffer, fromAddress)
                return true
            }
        }
    }
}
