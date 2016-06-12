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

import com.meibug.tunet.util.Log
import com.meibug.tunet.util.Log.Logger
import com.meibug.tunet.EndPoint

import junit.framework.TestCase
import org.junit.Assert

import java.util.ArrayList
import java.util.Timer
import java.util.TimerTask

abstract class KryoNetTestCase : TestCase() {

    private val threads: ArrayList<Thread> = ArrayList()
    internal var endPoints: ArrayList<EndPoint> = ArrayList()
    private var timer: Timer? = null
    private var fail: Boolean = false

    init {
        // Log.TRACE();
        // Log.DEBUG();
        Log.logger = object : Logger() {
            override fun log(level: Int, category: String?, message: String, ex: Throwable?) {
                // if (category == null || category.equals("kryonet")) //
                super.log(level, category, message, ex)
            }
        }
    }

    @Throws(Exception::class)
    override fun setUp() {
        println("---- " + javaClass.simpleName)
        timer = Timer()
    }

    @Throws(Exception::class)
    override fun tearDown() {
        timer!!.cancel()
    }

    fun startEndPoint(endPoint: EndPoint) {
        endPoints.add(endPoint)
        val thread = Thread(endPoint, endPoint.javaClass.simpleName)
        threads.add(thread)
        thread.start()
    }

    @JvmOverloads fun stopEndPoints(stopAfterMillis: Int = 0) {
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                for (endPoint in endPoints)
                    endPoint.stop()
                endPoints.clear()
            }
        }, stopAfterMillis.toLong())
    }

    fun waitForThreads(stopAfterMillis: Int) {
        if (stopAfterMillis > 10000) throw IllegalArgumentException("stopAfterMillis must be < 10000")
        stopEndPoints(stopAfterMillis)
        waitForThreads()
    }

    fun waitForThreads() {
        fail = false
        val failTask = object : TimerTask() {
            override fun run() {
                stopEndPoints()
                fail = true
            }
        }
        timer!!.schedule(failTask, 11000)
        while (true) {
            val iter = threads.iterator()
            while (iter.hasNext()) {
                if (!iter.next().isAlive) iter.remove()
            }
            if (threads.isEmpty()) break
            try {
                Thread.sleep(100)
            } catch (ignored: InterruptedException) {
            }

        }
        failTask.cancel()
        if (fail) Assert.fail("Test did not complete in a timely manner.")
        // Give sockets a chance to close before starting the next test.
        try {
            Thread.sleep(1000)
        } catch (ignored: InterruptedException) {
        }

    }

    companion object {
        val host = "localhost"
        val tcpPort = 54555
        val udpPort = 54777
    }
}
