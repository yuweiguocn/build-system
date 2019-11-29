package com.example.combinedSplits;

import static org.junit.Assert.*;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DemoActivityTest {
    @Rule
    public ActivityTestRule<DemoActivity> rule = new ActivityTestRule<>(DemoActivity.class);

    @Test
    public void testJniName() {
        final DemoActivity a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);

        assertFalse("unknown".equals(a.jniNameFromJNI()));
    }
}
