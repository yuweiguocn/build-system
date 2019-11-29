package com.example.SanAngeles;

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
    public void testGlView() {
        assertNotNull(rule.getActivity().mGLView);
    }
}
