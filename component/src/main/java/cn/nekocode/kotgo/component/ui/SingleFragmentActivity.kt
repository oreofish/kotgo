package cn.nekocode.kotgo.component.ui

import android.app.Fragment
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v7.widget.Toolbar
import android.util.TypedValue
import butterknife.bindView
import org.jetbrains.anko.*
import java.lang.reflect.ParameterizedType

abstract class SingleFragmentActivity<T: Fragment>: BaseActivity() {
    final var fragment: T? = null
    val fragmentClass: Class<T> = (this.javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>
    open val fragmentArguments by lazy {
        intent.extras
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragment()
    }

    private fun setupFragment() {
        fragment = checkAndAddFragment(containerId = ID_FRAGMENT_CONTENT, tag = fragmentClass.name,
                fragmentClass = fragmentClass, args = fragmentArguments)
    }
}
