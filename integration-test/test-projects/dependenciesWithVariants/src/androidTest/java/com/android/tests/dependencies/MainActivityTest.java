package com.android.tests.dependencies;

import com.android.tests.dependencies.jar.StringHelper;
import com.android.tests.dependencies.jar.StringHelper2;

import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    private TextView mTextView;

    @Before
    public void setUp() {
        final MainActivity a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.text);
    }

    /**
     * The name 'test preconditions' is a convention to signal that if this
     * test doesn't pass, the test case was not set up properly and it might
     * explain any and all failures in other tests.  This is not guaranteed
     * to run before other tests, as junit uses reflection to find the tests.
     */
    @SmallTest
    @Test    
    public void testPreconditions() {
        assertNotNull(mTextView);
    }

    @SmallTest
    @Test
    public void testMainActivity() {
        assertEquals("Foo-helper", mTextView.getText().toString());
    }

    @SmallTest
    @Test    
    public void testIndirectDependencies() {
        assertEquals("Foo-helper", StringHelper.getString("Foo"));
    }

    @SmallTest
    @Test    
    public void testDirectDependencies() {
        assertEquals("Foo-helper", StringHelper2.getString2("Foo"));
    }
}

