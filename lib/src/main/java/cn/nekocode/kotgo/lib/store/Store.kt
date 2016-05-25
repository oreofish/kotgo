package cn.nekocode.kotgo.lib.store

import android.content.Context
import com.orhanobut.hawk.Hawk
import com.orhanobut.hawk.HawkBuilder
import com.orhanobut.hawk.LogLevel

/**
 * Created by nekocode on 16/5/25.
 */
object Store {
    fun init(context: Context) {
        Hawk.init(context)
                .setEncryptionMethod(HawkBuilder.EncryptionMethod.MEDIUM)
                .setStorage(HawkBuilder.newSqliteStorage(context))
                .setLogLevel(LogLevel.FULL)
                .build()
    }

    operator fun <V> get(key: String): V? {
        return Hawk.get(key)
    }

    operator fun <V> set(key: String, value: V) {
        Hawk.put(key, value)
    }
}