package cn.nekocode.kotgo.lib.logger

import timber.log.Timber

/**
 * Created by nekocode on 16/5/25.
 */
object Logger {
    const val TAG = "Logger"
    const val LOGLEVEL = 6
    const val VERBOSE = 1
    const val DEBUG = 2
    const val INFO = 3
    const val WARN = 4
    const val ERROR = 5

    fun v(msg: String, tag: String=TAG) {
        if(LOGLEVEL > VERBOSE) {
            Timber.tag(tag).v(msg)
        }
    }

    fun d(msg: String, tag: String=TAG) {
        if(LOGLEVEL > DEBUG) {
            Timber.tag(tag).d(msg)
        }
    }

    fun i(msg: String, tag: String=TAG) {
        if(LOGLEVEL > INFO) {
            Timber.tag(tag).i(msg)
        }
    }

    fun w(msg: String, tag: String=TAG) {
        if(LOGLEVEL > WARN) {
            Timber.tag(tag).w(msg)
        }
    }

    fun e(msg: String, tag: String=TAG) {
        if(LOGLEVEL > ERROR) {
            Timber.tag(tag).e(msg)
        }
    }
}