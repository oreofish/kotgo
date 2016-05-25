package cn.nekocode.kotgo.lib.request.interceptor

import cn.nekocode.kotgo.lib.logger.Logger
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException
import java.util.concurrent.TimeUnit

/**
 * Created by nekocode on 16/5/25.
 */
class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response? {
        var logStr = ""
        val request = chain.request()

        val requestBody = request.body()
        val hasRequestBody = requestBody != null

        // Log http request
        val connection = chain.connection()
        val protocol = if (connection != null) connection.protocol() else Protocol.HTTP_1_1
        logStr += "--> " + request.method() + ' ' + request.url() + ' ' + protocol + '\n'

        // Log request body
        if(hasRequestBody) {
            val buffer = Buffer()
            requestBody.writeTo(buffer)

            var charset = Charset.forName("UTF-8")
            val contentType = requestBody.contentType()
            if (contentType != null) {
                charset = contentType.charset(charset)
            }

            logStr += buffer.readString(charset) + '\n'
        }

        // Process request
        val startNs = System.nanoTime()
        val response = chain.proceed(request)
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        // Log http response
        val responseBody = response.body()
        val contentLength = responseBody.contentLength()
        logStr += "<-- " + response.code() + ' ' + response.message() + /** ' ' + request.url() + **/
                " (" + tookMs + "ms" + ")\n"

        // Log response's body
        val source = responseBody.source()
        source.request(java.lang.Long.MAX_VALUE) // Buffer the entire body.
        val buffer = source.buffer()
        var charset = Charset.forName("UTF-8")
        val contentType = responseBody.contentType()
        if (contentType != null) {
            try {
                charset = contentType.charset(charset)
            } catch (e: UnsupportedCharsetException) {
                logStr += "Couldn't decode the response body; charset is likely malformed.\n"
                charset = null
            }
        }
        if (contentLength != 0L && charset != null) {
            logStr += buffer.clone().readString(charset) + '\n'
        }

        Logger.d(logStr)
        return response
    }
}