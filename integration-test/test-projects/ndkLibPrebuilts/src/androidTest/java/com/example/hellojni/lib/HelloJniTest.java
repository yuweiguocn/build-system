package com.example.hellojni.lib;

import static org.junit.Assert.*;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.example.hellojni.HelloJniTest \
 * com.example.hellojni.tests/android.test.InstrumentationTestRunner
 */
@RunWith(AndroidJUnit4.class)
public class HelloJniTest {
    @Rule
    public ActivityTestRule<HelloJni> rule = new ActivityTestRule<>(HelloJni.class);

    private TextView mTextView;

    @Test
    public void testJniName() {
        final HelloJni a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);

        assertFalse("unknown".equals(a.jniNameFromJNI()));
    }
}
