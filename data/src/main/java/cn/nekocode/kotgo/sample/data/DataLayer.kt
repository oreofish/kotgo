package cn.nekocode.kotgo.sample.data

import android.app.Application
import cn.nekocode.kotgo.lib.request.Request
import cn.nekocode.kotgo.lib.store.Store

/**
 * Created by nekocode on 2016/1/15.
 */
object DataLayer {
    lateinit var app: Application

    fun hook(app: Application) {
        DataLayer.app = app
        Store.init(app)
        Request.init(app)
    }
}