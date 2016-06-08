package com.meibug.tunet.util

import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/** A low overhead, lightweight logging system.
 * @author Nathan Sweet @n4te.com>
 */
object Log {
    /** No logging at all.  */
    val LEVEL_NONE = 6
    /** Critical errors. The application may no longer work correctly.  */
    val LEVEL_ERROR = 5
    /** Important warnings. The application will continue to work correctly.  */
    val LEVEL_WARN = 4
    /** Informative messages. Typically used for deployment.  */
    val LEVEL_INFO = 3
    /** Debug messages. This level is useful during development.  */
    val LEVEL_DEBUG = 2
    /** Trace messages. A lot of information is logged, so this level is usually only needed when debugging a problem.  */
    val LEVEL_TRACE = 1

    /** The level of messages that will be logged. Compiling this and the booleans below as "final" will cause the compiler to
     * remove all "if (Log.info) ..." type statements below the set level.  */
    private var level = LEVEL_INFO

    /** True when the ERROR level will be logged.  */
    var ERROR = level <= LEVEL_ERROR
    /** True when the WARN level will be logged.  */
    var WARN = level <= LEVEL_WARN
    /** True when the INFO level will be logged.  */
    var INFO = level <= LEVEL_INFO
    /** True when the DEBUG level will be logged.  */
    var DEBUG = level <= LEVEL_DEBUG
    /** True when the TRACE level will be logged.  */
    var TRACE = level <= LEVEL_TRACE

    /** Sets the level to log. If a version of this class is being used that has a final log level, this has no affect.  */
    fun set(level: Int) {
        // Comment out method contents when compiling fixed level JARs.
        Log.level = level
        ERROR = level <= LEVEL_ERROR
        WARN = level <= LEVEL_WARN
        INFO = level <= LEVEL_INFO
        DEBUG = level <= LEVEL_DEBUG
        TRACE = level <= LEVEL_TRACE
    }

    fun NONE() {
        set(LEVEL_NONE)
    }

    fun ERROR() {
        set(LEVEL_ERROR)
    }

    fun WARN() {
        set(LEVEL_WARN)
    }

    fun INFO() {
        set(LEVEL_INFO)
    }

    fun DEBUG() {
        set(LEVEL_DEBUG)
    }

    fun TRACE() {
        set(LEVEL_TRACE)
    }

    /** Sets the logger that will write the log messages.  */
    fun setLogger(logger: Logger) {
        Log.logger = logger
    }

    private var logger = Logger()

    fun error(message: String, ex: Throwable) {
        if (ERROR) logger.log(LEVEL_ERROR, null, message, ex)
    }

    fun error(category: String, message: String, ex: Throwable) {
        if (ERROR) logger.log(LEVEL_ERROR, category, message, ex)
    }

    fun error(message: String) {
        if (ERROR) logger.log(LEVEL_ERROR, null, message, null)
    }

    fun error(category: String, message: String) {
        if (ERROR) logger.log(LEVEL_ERROR, category, message, null)
    }

    fun warn(message: String, ex: Throwable) {
        if (WARN) logger.log(LEVEL_WARN, null, message, ex)
    }

    fun warn(category: String, message: String, ex: Throwable) {
        if (WARN) logger.log(LEVEL_WARN, category, message, ex)
    }

    fun warn(message: String) {
        if (WARN) logger.log(LEVEL_WARN, null, message, null)
    }

    fun warn(category: String, message: String) {
        if (WARN) logger.log(LEVEL_WARN, category, message, null)
    }

    fun info(message: String, ex: Throwable) {
        if (INFO) logger.log(LEVEL_INFO, null, message, ex)
    }

    fun info(category: String, message: String, ex: Throwable) {
        if (INFO) logger.log(LEVEL_INFO, category, message, ex)
    }

    fun info(message: String) {
        if (INFO) logger.log(LEVEL_INFO, null, message, null)
    }

    fun info(category: String, message: String) {
        if (INFO) logger.log(LEVEL_INFO, category, message, null)
    }

    fun debug(message: String, ex: Throwable) {
        if (DEBUG) logger.log(LEVEL_DEBUG, null, message, ex)
    }

    fun debug(category: String, message: String, ex: Throwable) {
        if (DEBUG) logger.log(LEVEL_DEBUG, category, message, ex)
    }

    fun debug(message: String) {
        if (DEBUG) logger.log(LEVEL_DEBUG, null, message, null)
    }

    fun debug(category: String, message: String) {
        if (DEBUG) logger.log(LEVEL_DEBUG, category, message, null)
    }

    fun trace(message: String, ex: Throwable) {
        if (TRACE) logger.log(LEVEL_TRACE, null, message, ex)
    }

    fun trace(category: String, message: String, ex: Throwable) {
        if (TRACE) logger.log(LEVEL_TRACE, category, message, ex)
    }

    fun trace(message: String) {
        if (TRACE) logger.log(LEVEL_TRACE, null, message, null)
    }

    fun trace(category: String, message: String) {
        if (TRACE) logger.log(LEVEL_TRACE, category, message, null)
    }

    /** Performs the actual logging. Default implementation logs to System.out. Extended and use [Log.logger] set to handle
     * logging differently.  */
    open class Logger {
        private val firstLogTime = Date().time

        open fun log(level: Int, category: String?, message: String, ex: Throwable?) {
            val builder = StringBuilder(256)

            val time = Date().time - firstLogTime
            val minutes = time / (1000 * 60)
            val seconds = time / 1000 % 60
            if (minutes <= 9) builder.append('0')
            builder.append(minutes)
            builder.append(':')
            if (seconds <= 9) builder.append('0')
            builder.append(seconds)

            when (level) {
                LEVEL_ERROR -> builder.append(" ERROR: ")
                LEVEL_WARN -> builder.append("  WARN: ")
                LEVEL_INFO -> builder.append("  INFO: ")
                LEVEL_DEBUG -> builder.append(" DEBUG: ")
                LEVEL_TRACE -> builder.append(" TRACE: ")
            }

            if (category != null) {
                builder.append('[')
                builder.append(category)
                builder.append("] ")
            }

            builder.append(message)

            if (ex != null) {
                val writer = StringWriter(256)
                ex.printStackTrace(PrintWriter(writer))
                builder.append('\n')
                builder.append(writer.toString().trim { it <= ' ' })
            }

            print(builder.toString())
        }

        /** Prints the message to System.out. Called by the default implementation of [.log].  */
        protected fun print(message: String) {
            println(message)
        }
    }
}