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

import java.net.DatagramPacket

interface ClientDiscoveryHandler {

    /**
     * Implementations of this method should return a new [DatagramPacket]
     * that the [Client] will use to fill with the incoming packet data
     * sent by the [ServerDiscoveryHandler].

     * @return a new [DatagramPacket]
     */
    fun onRequestNewDatagramPacket(): DatagramPacket

    /**
     * Called when the [Client] discovers a host.

     * @param datagramPacket
     * *            the same [DatagramPacket] from
     * *            [.onRequestNewDatagramPacket], after being filled with
     * *            the incoming packet data.
     * *
     * @param serialization
     * *            the [serialization] instance
     */
    fun onDiscoveredHost(datagramPacket: DatagramPacket, serialization: Serialization)

    /**
     * Called right before the [Client.discoverHost] or
     * [Client.discoverHosts] method exits. This allows the
     * implementation to clean up any resources used, i.e. an [Input].
     */
    fun onFinally()

    companion object {

        /**
         * This implementation of the [ClientDiscoveryHandler] is responsible
         * for providing the [Client] with it's default behavior.
         */
        val DEFAULT: ClientDiscoveryHandler = object : ClientDiscoveryHandler {

            override fun onRequestNewDatagramPacket(): DatagramPacket {
                return DatagramPacket(ByteArray(0), 0)
            }

            override fun onDiscoveredHost(datagramPacket: DatagramPacket, serialization: Serialization) {
                //
            }

            override fun onFinally() {
                //
            }

        }
    }

}
