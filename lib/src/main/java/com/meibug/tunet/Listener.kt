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

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.HashMap
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import com.meibug.tunet.util.Log.DEBUG
import com.meibug.tunet.util.Log.ERROR
import com.meibug.tunet.util.Log.debug
import com.meibug.tunet.util.Log.error

/** Used to be notified about connection events.  */
open class Listener {
    /** Called when the remote end has been connected. This will be invoked before any objects are received by
     * [.received]. This will be invoked on the same thread as [Client.update] and
     * [Server.update]. This method should not block for long periods as other network activity will not be processed
     * until it returns.  */
    open fun connected(connection: Connection) {
    }

    /** Called when the remote end is no longer connected. There is no guarantee as to what thread will invoke this method.  */
    open fun disconnected(connection: Connection) {
    }

    /** Called when an object has been received from the remote end of the connection. This will be invoked on the same thread as
     * [Client.update] and [Server.update]. This method should not block for long periods as other network
     * activity will not be processed until it returns.  */
    open fun received(connection: Connection, obj: Any) {
    }

    /** Called when the connection is below the [idle threshold][Connection.setIdleThreshold].  */
    open fun idle(connection: Connection) {
    }

    /** Uses reflection to called "received(Connection, XXX)" on the listener, where XXX is the received object type. Note this
     * class uses a HashMap lookup and (cached) reflection, so is not as efficient as writing a series of "instanceof" statements.  */
    class ReflectionListener : Listener() {
        private val classToMethod = HashMap<Any, Method?>()

        override fun received(connection: Connection, obj: Any) {
            val type = obj.javaClass
            var method: Method? = classToMethod.get(type)
            if (method == null) {
                if (classToMethod.containsKey(type)) return  // Only fail on the first attempt to find the method.
                try {
                    method = javaClass.getMethod("received")
                    method!!.isAccessible = true
                } catch (ex: SecurityException) {
                    if (ERROR) error("kryonet", "Unable to access method: received(Connection, " + type.name + ")", ex)
                    return
                } catch (ex: NoSuchMethodException) {
                    if (DEBUG)
                        debug("kryonet",
                                "Unable to find listener method: " + javaClass.name + "#received(Connection, " + type.name + ")")
                    return
                } finally {
                    classToMethod.put(type, method)
                }
            }
            try {
                method!!.invoke(this, connection, obj)
            } catch (ex: Throwable) {
                var newEx = ex
                if (ex is InvocationTargetException && ex.cause != null) newEx = ex.cause as Throwable
                if (newEx is RuntimeException) throw newEx
                throw RuntimeException("Error invoking method: " + javaClass.name + "#received(Connection, "
                        + type.name + ")", newEx)
            }

        }
    }

    /** Wraps a listener and queues notifications as [runnables][Runnable]. This allows the runnables to be processed on a
     * different thread, preventing the connection's update thread from being blocked.  */
    abstract class QueuedListener(internal val listener: Listener) : Listener() {

        init {
            if (listener == null) throw IllegalArgumentException("listener cannot be null.")
        }

        override fun connected(connection: Connection) {
            queue(Runnable { listener.connected(connection) })
        }

        override fun disconnected(connection: Connection) {
            queue(Runnable { listener.disconnected(connection) })
        }

        override fun received(connection: Connection, obj: Any) {
            queue(Runnable { listener.received(connection, obj) })
        }

        override fun idle(connection: Connection) {
            queue(Runnable { listener.idle(connection) })
        }

        protected abstract fun queue(runnable: Runnable)
    }

    /** Wraps a listener and processes notification events on a separate thread.  */
    class ThreadedListener
    /** Uses the specified threadPool to process notification events.  */
    @JvmOverloads constructor(listener: Listener, protected val threadPool: ExecutorService? = Executors.newFixedThreadPool(1)) : QueuedListener(listener) {

        init {
            if (threadPool == null) throw IllegalArgumentException("threadPool cannot be null.")
        }

        public override fun queue(runnable: Runnable) {
            threadPool?.execute(runnable)
        }
    }
    /** Creates a single thread to process notification events.  */

    /** Delays the notification of the wrapped listener to simulate lag on incoming objects. Notification events are processed on a
     * separate thread after a delay. Note that only incoming objects are delayed. To delay outgoing objects, use a LagListener at
     * the other end of the connection.  */
    class LagListener(private val lagMillisMin: Int, private val lagMillisMax: Int, listener: Listener) : QueuedListener(listener) {
        private val threadPool: ScheduledExecutorService
        internal val runnables: LinkedList<Runnable> = LinkedList()

        init {
            threadPool = Executors.newScheduledThreadPool(1)
        }

        public override fun queue(runnable: Runnable) {
            synchronized (runnables) {
                runnables.addFirst(runnable)
            }
            val lag = lagMillisMin + (Math.random() * (lagMillisMax - lagMillisMin)).toInt()
            threadPool.schedule({
                var runnable2: Runnable? = null
                synchronized (runnables) {
                    runnable2 = runnables.removeLast()
                }
                runnable2?.run()
            }, lag.toLong(), TimeUnit.MILLISECONDS)
        }
    }
}
