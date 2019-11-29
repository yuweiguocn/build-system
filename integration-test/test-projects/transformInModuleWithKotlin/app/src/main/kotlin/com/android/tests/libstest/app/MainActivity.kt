package com.android.tests.libstest.app

import StringUtil.android
import com.android.tests.libstest.lib.Lib

import android.app.Activity
import android.os.Bundle
import java.util.logging.Logger

class MainActivity : Activity() {
    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        App.handleTextView(this)
        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(
                Lib.someString().android())
    }
}
