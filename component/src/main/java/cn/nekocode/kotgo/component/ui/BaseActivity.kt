package cn.nekocode.kotgo.component.ui

import android.app.Fragment
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.annotation.CallSuper
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.TypedValue
import butterknife.bindView
import cn.nekocode.kotgo.component.rx.RxLifecycle
import org.jetbrains.anko.*
import java.lang.ref.WeakReference

abstract class BaseActivity: AppCompatActivity(), RxLifecycle.Impl {
    companion object {
        const val ID_TOOLBAR = 1
        const val ID_FRAGMENT_CONTENT = 2
    }

    final val toolbar: Toolbar by bindView(ID_TOOLBAR)
    open var toolbarLayoutId: Int? = null

    override final val lifecycle = RxLifecycle()
    val handler: GlobalHandler by lazy {
        GlobalHandler(this)
    }

    fun msg(message: Message) {
        Message().apply {
            copyFrom(message)
            handler.sendMessage(this)
        }
    }

    fun msgDelayed(message: Message, delayMillis: Long) {
        Message().apply {
            copyFrom(message)
            handler.sendMessageDelayed(this, delayMillis)
        }
    }

    fun runDelayed(delayMillis: Long, runnable: ()->Unit) {
        val msg = Message()
        msg.what = -101
        msg.arg1 = -102
        msg.arg2 = -103
        msg.obj = WeakReference<() -> Unit>(runnable)
        handler.sendMessageDelayed(msg, delayMillis)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        relativeLayout {
            if(toolbarLayoutId != null) {
                include<Toolbar>(toolbarLayoutId!!) {
                    id = ID_TOOLBAR
                }.lparams(width = matchParent, height = getToolbarSize())

                frameLayout {
                    id = ID_FRAGMENT_CONTENT
                }.lparams(width = matchParent, height = matchParent) {
                    below(ID_TOOLBAR)
                }

            } else {
                frameLayout {
                    id = ID_FRAGMENT_CONTENT
                }.lparams(width = matchParent, height = matchParent)

            }
        }

        if(toolbarLayoutId != null) {
            setSupportActionBar(toolbar)
        }
    }

    final fun getToolbarSize(): Int {
        TypedValue().apply {
            if(theme.resolveAttribute(android.R.attr.actionBarSize, this, true)) {
                return TypedValue.complexToDimensionPixelSize(this.data, resources.displayMetrics)
            }
        }

        return dip(50)
    }

    @CallSuper
    override fun onDestroy() {
        lifecycle.onDestory()
        super.onDestroy()
    }

    open fun handler(msg: Message) {

    }

    inline protected fun <reified T: BasePresenter> bindPresenter(args: Bundle? = null): T {
        val fragmentClass = T::class.java
        return checkAndAddFragment(0, fragmentClass.canonicalName, fragmentClass, args)
    }

    final protected fun <T: Fragment> checkAndAddFragment(
            containerId: Int, tag: String, fragmentClass: Class<T>, args: Bundle? = null): T {

        val trans = fragmentManager.beginTransaction()
        val className = fragmentClass.canonicalName

        var fragment = fragmentManager.findFragmentByTag(tag) as T?
        if (fragment?.isDetached ?: true) {
            fragment = Fragment.instantiate(this, className, args) as T

            trans.add(containerId, fragment, tag)
        }

        trans.commit()

        return fragment!!
    }

    class GlobalHandler(activity: BaseActivity): Handler() {
        private val mOuter = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            mOuter.get()?.apply {
                if (msg.what == -101 && msg.arg1 == -102 && msg.arg2 == -103) {
                    val runnable = (msg.obj as WeakReference<() -> Unit>).get()
                    runnable?.invoke()
                    return
                }

                this.handler(msg)
            }
        }
    }
}
