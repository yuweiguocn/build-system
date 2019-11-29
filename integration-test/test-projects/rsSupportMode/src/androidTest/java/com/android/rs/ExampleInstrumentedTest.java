package com.android.rs.support;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.v8.renderscript.RenderScript;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void canGetRS() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        RenderScript.create(appContext);
    }
}
