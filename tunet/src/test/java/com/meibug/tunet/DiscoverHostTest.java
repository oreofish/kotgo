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

import com.meibug.tunet.Client;
import com.meibug.tunet.ClientDiscoveryHandler;
import com.meibug.tunet.Connection;
import com.meibug.tunet.Listener;
import com.meibug.tunet.Serialization;
import com.meibug.tunet.Server;
import com.meibug.tunet.ServerDiscoveryHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static com.meibug.tunet.util.Log.*;
import static com.meibug.tunet.util.Log.info;

public class DiscoverHostTest extends KryoNetTestCase {

	public void testBroadcast () throws IOException {
		// This server exists solely to reply to Client#discoverHost.
		// It wouldn't be needed if the real server was using UDP.
		final Server broadcastServer = new Server();
		startEndPoint(broadcastServer);
		broadcastServer.bind(0, udpPort);

		final Server server = new Server();
		startEndPoint(server);
		server.bind(54555);
		server.addListener(new Listener() {
			public void disconnected (Connection connection) {
				broadcastServer.stop();
				server.stop();
			}
		});

		// ----

		Client client = new Client();
		InetAddress host = client.discoverHost(udpPort, 2000);
		if (host == null) {
			stopEndPoints();
			fail("No servers found.");
			return;
		}

		startEndPoint(client);
		client.connect(2000, host, tcpPort);
		client.stop();

		waitForThreads();
	}

	public void testCustomBroadcast () throws IOException {

		ServerDiscoveryHandler serverDiscoveryHandler = new ServerDiscoveryHandler() {
			@Override
			public boolean onDiscoverHost (DatagramChannel datagramChannel, InetSocketAddress fromAddress,
				Serialization serialization) throws IOException {

				DiscoveryResponsePacket packet = new DiscoveryResponsePacket();
				packet.id = 42;
				packet.gameName = "gameName";
				packet.playerName = "playerName";

				ByteBuffer buffer = ByteBuffer.allocate(256);
				serialization.write(null, buffer, packet);
				buffer.flip();

				datagramChannel.send(buffer, fromAddress);

				return true;
			}
		};

		ClientDiscoveryHandler clientDiscoveryHandler = new ClientDiscoveryHandler() {
			private Input input = null;

			@Override
			public DatagramPacket onRequestNewDatagramPacket () {
				byte[] buffer = new byte[1024];
				input = new Input(buffer);
				return new DatagramPacket(buffer, buffer.length);
			}

			@Override
			public void onDiscoveredHost (DatagramPacket datagramPacket, Serialization serialization) {
				if (input != null) {
					DiscoveryResponsePacket packet;
					packet = (DiscoveryResponsePacket)serialization.readClassAndObject(input);
					info("test", "packet.id = " + packet.id);
					info("test", "packet.gameName = " + packet.gameName);
					info("test", "packet.playerName = " + packet.playerName);
					info("test", "datagramPacket.getAddress() = " + datagramPacket.getAddress());
					info("test", "datagramPacket.getPort() = " + datagramPacket.getPort());
					assertEquals(42, packet.id);
					assertEquals("gameName", packet.gameName);
					assertEquals("playerName", packet.playerName);
					assertEquals(udpPort, datagramPacket.getPort());
				}
			}

			@Override
			public void onFinally () {
				if (input != null) {
					input.close();
				}
			}
		};

		// This server exists solely to reply to Client#discoverHost.
		// It wouldn't be needed if the real server was using UDP.
		final Server broadcastServer = new Server();

		// broadcastServer.getSerialization().register(DiscoveryResponsePacket.class);
		broadcastServer.setDiscoveryHandler(serverDiscoveryHandler);

		startEndPoint(broadcastServer);
		broadcastServer.bind(0, udpPort);

		final Server server = new Server();
		startEndPoint(server);
		server.bind(54555);
		server.addListener(new Listener() {
			public void disconnected (Connection connection) {
				broadcastServer.stop();
				server.stop();
			}
		});

		// ----

		Client client = new Client();

		// client.getSerialization().register(DiscoveryResponsePacket.class);
		client.setDiscoveryHandler(clientDiscoveryHandler);

		InetAddress host = client.discoverHost(udpPort, 2000);
		if (host == null) {
			stopEndPoints();
			fail("No servers found.");
			return;
		}

		startEndPoint(client);
		client.connect(2000, host, tcpPort);
		client.stop();

		waitForThreads();
	}

	public static class DiscoveryResponsePacket {

		public DiscoveryResponsePacket () {
			//
		}

		public int id;
		public String gameName;
		public String playerName;
	}

	public static class Input extends InputStream {
		protected byte[] buffer;
		protected int position;
		protected int capacity;
		protected int limit;
		protected long total;
		protected char[] chars = new char[32];
		protected InputStream inputStream;

		public Input (byte[] buffer) {
			if (buffer == null) throw new IllegalArgumentException("bytes cannot be null.");
			buffer = buffer;
			position = 0;
			limit = buffer.length;
			capacity = buffer.length;
			total = 0;
			inputStream = null;
		}

		@Override
		public int read() throws IOException {
			return 0;
		}

		public void close () {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException ignored) {
				}
			}
		}
	}
}
