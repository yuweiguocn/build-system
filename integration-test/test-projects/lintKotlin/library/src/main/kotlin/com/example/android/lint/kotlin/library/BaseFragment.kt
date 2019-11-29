package com.example.android.lint.kotlin.library

import android.app.Fragment

open class BaseFragment : Fragment() {

    @android.support.annotation.CallSuper
    open fun foo() {
    }
}
