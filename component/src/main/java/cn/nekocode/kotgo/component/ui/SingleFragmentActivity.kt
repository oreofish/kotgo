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
    companion object {
        const val ID_TOOLBAR = 1
        const val ID_FRAGMENT_CONTENT = 2
    }

    final val toolbar: Toolbar by bindView(ID_TOOLBAR)
    open var toolbarLayoutId: Int? = null

    final var fragment: T? = null
    val fragmentClass: Class<T> = (this.javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>
    open val fragmentArguments by lazy {
        intent.extras
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

        setupFragment()

        if(toolbarLayoutId != null) {
            setSupportActionBar(toolbar)
        }
    }

    private final fun getToolbarSize(): Int {
        TypedValue().apply {
            if(theme.resolveAttribute(android.R.attr.actionBarSize, this, true)) {
                return TypedValue.complexToDimensionPixelSize(this.data, resources.displayMetrics)
            }
        }

        return dip(50)
    }

    private fun setupFragment() {
        fragment = checkAndAddFragment(containerId = ID_FRAGMENT_CONTENT, tag = fragmentClass.name,
                fragmentClass = fragmentClass, args = fragmentArguments)
    }
}
