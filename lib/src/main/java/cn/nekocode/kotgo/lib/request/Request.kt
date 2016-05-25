package cn.nekocode.kotgo.lib.request

import android.content.Context
import android.widget.ImageView
import cn.nekocode.kotgo.lib.request.interceptor.LoggingInterceptor
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Created by nekocode on 16/5/25.
 */
object Request {
    const val RESPONSE_CACHE_FILE: String = "reponse_cache"
    const val RESPONSE_CACHE_SIZE = 10 * 1024 * 1024L
    const val HTTP_CONNECT_TIMEOUT = 10L
    const val HTTP_READ_TIMEOUT = 30L
    const val HTTP_WRITE_TIMEOUT = 10L

    val gson: Gson =
            GsonBuilder().setDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss'.'SSS'Z'").create()

    lateinit var client: OkHttpClient

    fun init(context: Context) {
        val cacheDir = File(context.cacheDir, RESPONSE_CACHE_FILE)

        client = OkHttpClient.Builder()
                .cache(Cache(cacheDir, RESPONSE_CACHE_SIZE))
                .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(HTTP_WRITE_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(HTTP_READ_TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(LoggingInterceptor())
                .addNetworkInterceptor(StethoInterceptor())
                .build()

        Stetho.initializeWithDefaults(context)
    }

    fun image(imageView: ImageView, url: String) {
        Picasso.with(imageView.context).load(url).centerCrop().fit().into(imageView)
    }
}