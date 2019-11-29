package com.android.tests.libstest.lib

import android.app.Activity
import android.os.Bundle
import StringUtil.android
import java.util.logging.Logger

class MainActivity : Activity() {
    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lib_main)

        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(Lib.someString().android())
    }
}
