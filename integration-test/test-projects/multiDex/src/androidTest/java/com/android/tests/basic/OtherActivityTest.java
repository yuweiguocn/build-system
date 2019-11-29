package com.android.tests.basic;

import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OtherActivityTest {
    @Rule public ActivityTestRule<OtherActivity> rule = new ActivityTestRule<>(OtherActivity.class);

    private TextView mTextView;

    @Before
    public void setUp() {
        final OtherActivity a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        Assert.assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.text);

    }

    /**
     * The name 'test preconditions' is a convention to signal that if this test doesn't pass, the
     * test case was not set up properly and it might explain any and all failures in other tests.
     * This is not guaranteed to run before other tests, as junit uses reflection to find the tests.
     */
    @Test
    @MediumTest
    public void testPreconditions() {
        Assert.assertNotNull(mTextView);
    }

    @Test
    @MediumTest
    public void testBuildConfig() {
        Assert.assertEquals("bar", BuildConfig.FOO);
    }

    @Test
    public void testDependency() {
      // Make sure JUnit 4 is on the classpath, which means multidex works.
      org.junit.Assert.assertEquals(4, 2+2);
    }
}

