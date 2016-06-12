package com.meibug.tunet.log

import com.meibug.tunet.util.Log
import junit.framework.TestCase
import java.util.*

/**
 * Created by xing on 16/6/12.
 */
class SpeedTest : TestCase(){
    private var timer: Timer? = null
    private val times = 100*1000*1000

    init {
        Log.set(Log.LEVEL_INFO)
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

    fun testDebug() {
        val start = System.currentTimeMillis();
        for(i in 0..times) {
            if(Log.DEBUG) Log.debug("log message ${i}")
        }
        val end = System.currentTimeMillis();
        val debugSpend = end - start

        Log.info("${times} debug logs, Time spend ${debugSpend}ms")
    }

    fun testTrace() {
        val start = System.currentTimeMillis();
        for(i in 0..times) {
            Log.trace("log message ${i}")
        }
        val end = System.currentTimeMillis();
        val debugSpend = end - start

        Log.info("${times} trace logs, Time spend ${debugSpend}ms")
    }

}